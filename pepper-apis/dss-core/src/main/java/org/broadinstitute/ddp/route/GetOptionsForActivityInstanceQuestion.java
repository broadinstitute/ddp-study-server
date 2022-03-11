package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiPicklistOption;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.json.RemoteAutoCompleteResponse;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.PicklistOptionTypeaheadComparator;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *  This route returns the list of suggestions for the supplied Picklist Option label
 */
public class GetOptionsForActivityInstanceQuestion implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetOptionsForActivityInstanceQuestion.class);
    private static final int DEFAULT_LIMIT = 100;
    private final I18nContentRenderer renderer;

    public GetOptionsForActivityInstanceQuestion(I18nContentRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public RemoteAutoCompleteResponse handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);
        String questionStableId = request.params(RouteConstants.PathParam.QUESTION_STABLE_ID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), participantGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);
        String autoCompleteQuery = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY);
        String queryLimit = request.queryParams(RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT);
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.valueOf(queryLimit) : DEFAULT_LIMIT;
        ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
        LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);

        LOG.info("Fetching auto complete picklist options for activity instance {} and participant {} in study {} by operator {} "
                + "(isStudyAdmin={})", instanceGuid, participantGuid, studyGuid, operatorGuid, isStudyAdmin);

        RemoteAutoCompleteResponse result = TransactionWrapper.withTxn(handle -> {

            //load All options first
            List<PicklistOption> allOptions = new ArrayList<>();
            List<PicklistOption> suggestions;
            JdbiPicklistOption jdbiPicklistOption = handle.attach(JdbiPicklistOption.class);
            JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
            Optional<Long> questionId = jdbiQuestion.findIdByStableIdAndInstanceGuid(questionStableId, instanceGuid);
            List<PicklistOptionDto> optionDtos = jdbiPicklistOption.findAllActiveOrderedOptionsByQuestionId(questionId.get());
            for (PicklistOptionDto optionDto : optionDtos) {
                PicklistOption option = new PicklistOption(optionDto.getStableId(), optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.isAllowDetails(), optionDto.isExclusive(), optionDto.isDefault());
                allOptions.add(option);
            }
            // Render labels using current time to get latest templates.
            long timestamp = Instant.now().toEpochMilli();
            long langCodeId = preferredUserLanguage.getId();
            renderer.bulkRenderAndApply(handle, allOptions, style, langCodeId, timestamp);

            if (StringUtils.isBlank(autoCompleteQuery)) {
                LOG.info("Option suggestion query is blank, returning all results");
                suggestions = allOptions.stream().limit(limit).collect(Collectors.toList());
            } else {
                suggestions = filterOptions(allOptions, autoCompleteQuery, limit);
            }

            return new RemoteAutoCompleteResponse(autoCompleteQuery, suggestions);
        });

        return result;
    }

    private List<PicklistOption> filterOptions(List<PicklistOption> options, String autoCompleteQuery, int limit) {
        List<PicklistOption> optionMatches = new ArrayList<>();

        // first pass: find & filter simple matches
        for (PicklistOption option : options) {
            if (option.getOptionLabel().toUpperCase().contains(autoCompleteQuery.toUpperCase())) {
                optionMatches.add(option);
            }
        }

        // now sort the matches in a way that puts left-most matches near the top, favoring word start matches
        List<PicklistOption> sortedSuggestions = new ArrayList<>();
        optionMatches.stream().sorted(new PicklistOptionTypeaheadComparator(autoCompleteQuery)).limit(limit).forEach(option -> {
            sortedSuggestions.add(option);
        });

        return sortedSuggestions;
    }

}
