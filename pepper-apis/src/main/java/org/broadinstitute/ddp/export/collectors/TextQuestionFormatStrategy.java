package org.broadinstitute.ddp.export.collectors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;

/**
 * Rule:
 * - single column for question response
 * - value formatted as text
 * - null value results in empty cell
 */
public class TextQuestionFormatStrategy implements ResponseFormatStrategy<TextQuestionDef, TextAnswer> {

    @Override
    public Map<String, Object> mappings(TextQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newTextType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(TextQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().render("en")));
        return props;
    }

    @Override
    public List<String> headers(TextQuestionDef definition) {
        return Arrays.asList(definition.getStableId());
    }

    public List<String> headers(TextQuestionDef definition, int number) {
        return Arrays.asList(definition.getStableId() + "_" + number);
    }

    @Override
    public Map<String, String> collect(TextQuestionDef question, TextAnswer answer) {
        Map<String, String> record = new HashMap<>();
        record.put(question.getStableId(), StringUtils.defaultString(answer.getValue(), ""));
        return record;
    }
}
