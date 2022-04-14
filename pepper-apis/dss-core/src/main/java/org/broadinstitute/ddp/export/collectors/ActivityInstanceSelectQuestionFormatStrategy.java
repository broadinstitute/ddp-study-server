package org.broadinstitute.ddp.export.collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.ActivityInstanceService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule:
 * - single column for question response
 * - value formatted as text
 * - null value results in empty cell
 */
public class ActivityInstanceSelectQuestionFormatStrategy implements ResponseFormatStrategy<ActivityInstanceSelectQuestionDef,
        ActivityInstanceSelectAnswer> {

    private final ActivityInstanceService activityInstanceService;
    private final String studyGuid;

    public ActivityInstanceSelectQuestionFormatStrategy(String studyGuid) {
        this.studyGuid = studyGuid;
        ActivityInstanceDao activityInstanceDao = new ActivityInstanceDao();
        PexInterpreter interpreter = new TreeWalkInterpreter();
        I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();
        this.activityInstanceService = new ActivityInstanceService(activityInstanceDao, interpreter, i18nContentRenderer);
    }

    @Override
    public Map<String, Object> mappings(ActivityInstanceSelectQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newTextType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(ActivityInstanceSelectQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().renderWithDefaultValues("en")));
        return props;
    }

    @Override
    public List<String> headers(ActivityInstanceSelectQuestionDef definition) {
        return Collections.singletonList(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(ActivityInstanceSelectQuestionDef question, ActivityInstanceSelectAnswer answer) {
        return collect(question, answer, null);
    }

    public Map<String, String> collect(ActivityInstanceSelectQuestionDef question, ActivityInstanceSelectAnswer answer, Long userId) {
        Map<String, String> record = new HashMap<>();
        String name;
        if (userId != null && studyGuid != null) {
            name = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
                User user = handle.attach(UserDao.class).findUserById(userId).orElseThrow();
                List<ActivityInstanceSummary> summaries = activityInstanceService.findTranslatedInstanceSummaries(
                        handle, user.getGuid(), studyGuid, Set.copyOf(question.getActivityCodes()),
                        LanguageStore.getDefault().getIsoCode()).stream()
                        .filter(i -> i.getActivityInstanceGuid().equals(answer.getValue())).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(summaries)) {
                    Map<String, FormResponse> responses = activityInstanceService.countQuestionsAndAnswers(
                            handle, user.getGuid(), user.getGuid(), studyGuid, summaries);
                    activityInstanceService.renderInstanceSummaries(handle, user.getId(), user.getGuid(), studyGuid, summaries, responses);
                    return summaries.get(0).getActivityName();
                }
                return answer.getValue();
            });
        } else {
            name = answer.getValue();
        }
        record.put(question.getStableId(), StringUtils.defaultString(name, ""));
        return record;
    }
}
