package org.broadinstitute.ddp.route;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.RemoteAutoCompleteResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.StringSuggestionTypeaheadComparator;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This route returns the list of suggestions for the supplied Picklist Option label
 */
@Slf4j
public class GetOptionsForActivityInstanceQuestionRoute implements Route {
    private static final int DEFAULT_LIMIT = 100;
    private final I18nContentRenderer renderer;

    public GetOptionsForActivityInstanceQuestionRoute(I18nContentRenderer renderer) {
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
        int limit = StringUtils.isNotBlank(queryLimit) ? Integer.parseInt(queryLimit) : DEFAULT_LIMIT;
        if (limit > DEFAULT_LIMIT) {
            ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(
                    ErrorCodes.OPERATION_NOT_ALLOWED, "Supported max limit is only " + DEFAULT_LIMIT));
        }
        ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
        LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);

        log.info("Fetching auto complete picklist options for activity instance {} and participant {} in study {} by operator {} "
                + "(isStudyAdmin={})", instanceGuid, participantGuid, studyGuid, operatorGuid, isStudyAdmin);

        return TransactionWrapper.withTxn(handle -> {

            //Get all options first from ActivityDef (already in memory) then render and match
            long timestamp = Instant.now().toEpochMilli();
            long langCodeId = preferredUserLanguage.getId();
            List<PicklistOption> suggestions;
            List<PicklistOption> allOptions = getPicklistOptions(handle, studyGuid, instanceGuid, questionStableId);
            if (StringUtils.isBlank(autoCompleteQuery)) {
                log.info("Option suggestion query is blank, returning all results size to default limit");
                suggestions = allOptions.stream().limit(limit).collect(Collectors.toList());
                renderer.bulkRenderAndApply(handle, suggestions, style, langCodeId, timestamp);
            } else {
                renderer.bulkRenderAndApply(handle, allOptions, style, langCodeId, timestamp);
                suggestions = filterOptions(allOptions, autoCompleteQuery, limit);
            }

            return new RemoteAutoCompleteResponse(autoCompleteQuery, suggestions);
        });
    }

    private List<PicklistOption> getPicklistOptions(Handle handle, String studyGuid,
                                                    String activityInstanceGuid, String questionStableId) {

        Optional<ActivityInstanceDto> instanceDtoOpt = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid);
        if (instanceDtoOpt.isEmpty()) {
            ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(
                    ErrorCodes.ACTIVITY_NOT_FOUND, "Activity not found for Instance: " + activityInstanceGuid));
        }

        Optional<FormActivityDef> formActivityDefOpt = ActivityDefStore.getInstance()
                .findActivityDef(handle, studyGuid, instanceDtoOpt.get());
        if (formActivityDefOpt.isEmpty()) {
            ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(
                    ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, "Could not find activity def for studyGuid: " + studyGuid
                    + " and instanceGuid: " + activityInstanceGuid));
        }

        QuestionDef questionDef = formActivityDefOpt.get().getQuestionByStableId(questionStableId);
        if (questionDef.getQuestionType() != QuestionType.PICKLIST) {
            ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(
                    ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, "Not a Picklist question stableID: " + questionStableId));
        }
        List<PicklistOptionDef> optionDefs = ((PicklistQuestionDef) questionDef).getRemotePicklistOptions();

        return optionDefs.stream().map(optionDef -> new PicklistOption(optionDef.getStableId(),
                optionDef.getOptionLabelTemplate().getTemplateId(),
                optionDef.getTooltipTemplate() != null ? optionDef.getTooltipTemplate().getTemplateId() : null,
                optionDef.getDetailLabelTemplate() != null ? optionDef.getDetailLabelTemplate().getTemplateId() : null,
                optionDef.isDetailsAllowed(), optionDef.isExclusive(), optionDef.isDefault()
        )).collect(Collectors.toList());
    }

    private List<PicklistOption> filterOptions(List<PicklistOption> options, String autoCompleteQuery, int limit) {
        // first pass: find & filter simple matches
        List<PicklistOption> optionMatches = options.stream().filter(option -> option.getOptionLabel().toUpperCase()
                .contains(autoCompleteQuery.toUpperCase())).collect(Collectors.toList());

        //include options with allowDetails=true if not already included
        optionMatches.addAll(options.stream().filter(option -> option.isDetailsAllowed() && !optionMatches.contains(option))
                .collect(Collectors.toList()));

        // now sort the matches in a way that puts left-most matches near the top, favoring word start matches
        // apply limit and return results
        var suggestionComparator = new StringSuggestionTypeaheadComparator(autoCompleteQuery);
        return optionMatches.stream()
                .sorted((lhs, rhs) -> suggestionComparator.compare(lhs.getOptionLabel(), rhs.getOptionLabel()))
                .limit(limit).collect(Collectors.toList());
    }

}
