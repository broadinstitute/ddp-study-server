package org.broadinstitute.ddp.export.collectors;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;

public class NumericQuestionFormatStrategy implements ResponseFormatStrategy<NumericQuestionDef, NumericAnswer> {

    @Override
    public Map<String, Object> mappings(NumericQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(definition.getStableId(), MappingUtil.newLongType());

        return props;
    }

    @Override
    public Map<String, Object> questionDef(NumericQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().renderWithDefaultValues("en")));
        return props;
    }

    @Override
    public List<String> headers(NumericQuestionDef definition) {
        return List.of(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(NumericQuestionDef question, NumericAnswer answer) {
        Map<String, String> record = new HashMap<>();
        if (answer.getValue() != null) {
            record.put(question.getStableId(), answer.getValue().toString());
        }
        return record;
    }
}
