package org.broadinstitute.ddp.export.collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule:
 * - single column for question response
 * - value formatted as text
 * - null value results in empty cell
 */
public class ActivityInstanceSelectQuestionFormatStrategy implements ResponseFormatStrategy<ActivityInstanceSelectQuestionDef,
        ActivityInstanceSelectAnswer> {

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
        return Arrays.asList(definition.getStableId());
    }

    // TODO: Collect Activity Instance name
    @Override
    public Map<String, String> collect(ActivityInstanceSelectQuestionDef question, ActivityInstanceSelectAnswer answer) {
        Map<String, String> record = new HashMap<>();
        record.put(question.getStableId(), StringUtils.defaultString(answer.getValue(), ""));
        return record;
    }
}
