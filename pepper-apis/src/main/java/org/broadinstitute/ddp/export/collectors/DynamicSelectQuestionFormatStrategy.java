package org.broadinstitute.ddp.export.collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.DynamicSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DynamicSelectAnswer;

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
public class DynamicSelectQuestionFormatStrategy implements ResponseFormatStrategy<DynamicSelectQuestionDef,
        DynamicSelectAnswer> {

    @Override
    public Map<String, Object> mappings(DynamicSelectQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newTextType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(DynamicSelectQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().renderWithDefaultValues("en")));
        return props;
    }

    @Override
    public List<String> headers(DynamicSelectQuestionDef definition) {
        return Arrays.asList(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(DynamicSelectQuestionDef question, DynamicSelectAnswer answer) {
        Map<String, String> record = new HashMap<>();
        record.put(question.getStableId(), StringUtils.defaultString(answer.getValue(), ""));
        return record;
    }
}
