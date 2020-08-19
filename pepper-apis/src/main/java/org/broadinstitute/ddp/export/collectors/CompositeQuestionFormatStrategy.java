package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;

/**
 * Rule:
 * - single column for entire composite answer
 * - each "row" of the composite answer is pipe-separated
 * - each "element" in a "row" is semicolon-separated
 * - delimiters are kept even if a value is missing
 * - no answers result in empty cell
 */
public class CompositeQuestionFormatStrategy implements ResponseFormatStrategy<CompositeQuestionDef, CompositeAnswer> {

    @Override
    public Map<String, Object> mappings(CompositeQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newTextType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(CompositeQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().render("en")));
        props.put("allowMultiple", definition.isAllowMultiple());
        //watchout: children not handled here
        return props;
    }

    @Override
    public List<String> headers(CompositeQuestionDef definition) {
        return Arrays.asList(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(CompositeQuestionDef question, CompositeAnswer answer) {
        Map<String, String> record = new HashMap<>();

        List<String> rows = new ArrayList<>();
        for (AnswerRow row : answer.getValue()) {
            if (row == null) {
                continue;
            }

            Map<String, String> values = new HashMap<>();
            for (Answer element : row.getValues()) {
                if (element == null) {
                    continue;
                }
                String value = formatBasicAnswer(element);
                values.put(element.getQuestionStableId(), value);
            }

            List<String> aligned = new ArrayList<>();
            for (QuestionDef child : question.getChildren()) {
                aligned.add(values.getOrDefault(child.getStableId(), ""));
            }

            rows.add(String.join(";", aligned));
        }

        record.put(question.getStableId(), String.join("|", rows));
        return record;
    }

    private String formatBasicAnswer(Answer answer) {
        switch (answer.getQuestionType()) {
            case AGREEMENT:
                Boolean agreeValue = ((AgreementAnswer) answer).getValue();
                return (agreeValue == null ? "" : agreeValue.toString());
            case BOOLEAN:
                Boolean boolValue = ((BoolAnswer) answer).getValue();
                return (boolValue == null ? "" : boolValue.toString());
            case TEXT:
                return StringUtils.defaultString(((TextAnswer) answer).getValue(), "");
            case NUMERIC:
                return (answer.getValue() == null ? "" : answer.getValue().toString());
            case DATE:
                DateValue dateValue = ((DateAnswer) answer).getValue();
                return (dateValue == null ? null : dateValue.toDefaultDateFormat());
            case PICKLIST:
                String detailText = null;   // Only supports one detail text for now.
                List<String> ids = new ArrayList<>();
                for (SelectedPicklistOption option : ((PicklistAnswer) answer).getValue()) {
                    if (option.getDetailText() != null) {
                        detailText = option.getDetailText();
                    }
                    ids.add(option.getStableId());
                }
                if (detailText != null) {
                    ids.add(detailText);
                }
                return String.join(",", ids);
            case COMPOSITE:
                throw new DDPException("Composite-inside-composite is not supported in data export");
            default:
                throw new DDPException("Unhandled answer type " + answer.getQuestionType());
        }
    }
}
