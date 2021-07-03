package org.broadinstitute.ddp.content;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;


/**
 * Utility methods for rendering selected picklist options and picklist option detail text.
 */
public class SelectedPickListOptionRenderUtil {

    public static String selectedOptionLabelsRender(QuestionDef questionDef, Answer answer, String fallbackValue, String isoLangCode) {
        Map<String, PicklistOptionDef> options = ((PicklistQuestionDef) questionDef)
                .getAllPicklistOptions().stream()
                .collect(Collectors.toMap(PicklistOptionDef::getStableId, Function.identity()));
        return ((PicklistAnswer) answer).getValue().stream()
                .map(selected -> renderOptionLabelOrFallback(options, selected, fallbackValue, isoLangCode))
                .collect(Collectors.joining(","));
    }

    public static String selectedOptionLabelsRender(Question question, Answer answer, String fallbackValue) {
        Map<String, String> options = ((PicklistQuestion) question)
                .streamAllPicklistOptions()
                .collect(Collectors.toMap(PicklistOption::getStableId,
                        (p) -> p.getOptionLabel() == null ? fallbackValue : p.getOptionLabel()));
        return ((PicklistAnswer) answer).getValue().stream()
                .map(selected -> options.get(selected.getStableId()))
                .collect(Collectors.joining(","));
    }

    public static String detailTextRender(QuestionDef questionDef, Answer answer, String fallbackValue, String isoLangCode) {
        String detailText = getDetailText(answer);
        return detailText != null ? detailText : selectedOptionLabelsRender(questionDef, answer, fallbackValue, isoLangCode);
    }

    public static String detailTextRender(Question question, Answer answer, String fallbackValue) {
        String detailText = getDetailText(answer);
        return detailText != null ? detailText : selectedOptionLabelsRender(question, answer, fallbackValue);
    }

    private static String getDetailText(Answer answer) {
        String detailText = null;
        for (SelectedPicklistOption option : ((PicklistAnswer) answer).getValue()) {
            if (option.getDetailText() != null) {
                detailText = option.getDetailText();
            }
        }
        return  detailText;
    }

    private static String renderOptionLabelOrFallback(Map<String, PicklistOptionDef> options, SelectedPicklistOption selected,
                                                      String fallbackValue, String isoLangCode) {
        PicklistOptionDef option = options.get(selected.getStableId());
        return option.getOptionLabelTemplate() == null ? fallbackValue : option.getOptionLabelTemplate().render(isoLangCode);
    }
}
