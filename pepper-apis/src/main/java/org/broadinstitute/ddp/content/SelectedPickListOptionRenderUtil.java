package org.broadinstitute.ddp.content;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.joinWithComma;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
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

    public static String selectedOptionsRender(QuestionDef questionDef, Answer answer, String isoLangCode,
                                               boolean useDetailTextForPickList) {
        Map<String, PicklistOptionDef> options = ((PicklistQuestionDef) questionDef)
                .getAllPicklistOptions().stream()
                .collect(Collectors.toMap(PicklistOptionDef::getStableId, Function.identity()));
        return ((PicklistAnswer) answer).getValue().stream()
                .map(selected -> optionRender(selected, options, isoLangCode, useDetailTextForPickList))
                .collect(joinWithComma());
    }

    public static String selectedOptionsRender(Question question, Answer answer, boolean useDetailTextForPickList) {
        Map<String, String> optionLabels = ((PicklistQuestion) question).streamAllPicklistOptions()
                .collect(Collectors.toMap(PicklistOption::getStableId, (p) -> p.getOptionLabel()));
        return ((PicklistAnswer) answer).getValue().stream()
                .map(selected -> optionRender(selected, optionLabels, useDetailTextForPickList))
                .collect(joinWithComma());
    }

    private static String optionRender(SelectedPicklistOption selected, Map<String, PicklistOptionDef> options,
                                       String isoLangCode, boolean useDetailTextForPickList) {
        if (useDetailTextForPickList) {
            return StringUtils.isNotBlank(selected.getDetailText()) ? selected.getDetailText()
                    : options.get(selected.getStableId()).getOptionLabelTemplate().render(isoLangCode);
        } else {
            return options.get(selected.getStableId()).getOptionLabelTemplate().render(isoLangCode);
        }
    }

    private static String optionRender(SelectedPicklistOption selected, Map<String, String> optionLabels,
                                       boolean useDetailTextForPickList) {
        if (useDetailTextForPickList) {
            return StringUtils.isNotBlank(selected.getDetailText()) ? selected.getDetailText()
                    : optionLabels.get(selected.getStableId());
        } else {
            return optionLabels.get(selected.getStableId());
        }
    }
}
