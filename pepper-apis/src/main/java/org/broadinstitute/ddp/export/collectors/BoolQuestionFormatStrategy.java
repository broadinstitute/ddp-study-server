package org.broadinstitute.ddp.export.collectors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;

/**
 * Rule:
 * - single column for question response
 * - value formatted as boolean text 'true' or 'false'
 * - null value results in empty cell
 */
public class BoolQuestionFormatStrategy implements ResponseFormatStrategy<BoolQuestionDef, BoolAnswer> {

    @Override
    public Map<String, Object> mappings(BoolQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newBoolType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(BoolQuestionDef  definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().render("en")));
        return props;
    }

    @Override
    public List<String> headers(BoolQuestionDef definition) {
        return Arrays.asList(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(BoolQuestionDef question, BoolAnswer answer) {
        Map<String, String> record = new HashMap<>();
        String value = answer.getValue() == null ? "" : answer.getValue().toString();
        record.put(question.getStableId(), value);
        return record;
    }
}
