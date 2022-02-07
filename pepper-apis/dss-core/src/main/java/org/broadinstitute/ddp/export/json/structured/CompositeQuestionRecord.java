package org.broadinstitute.ddp.export.json.structured;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class CompositeQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<List<String>> answer = new ArrayList<>();

    public CompositeQuestionRecord(String stableId, List<AnswerRow> answer) {
        super(QuestionType.COMPOSITE, stableId);
        if (CollectionUtils.isNotEmpty(answer)) {
            for (AnswerRow answerRow : answer) {
                List<String> row = new ArrayList<>();
                for (Answer ans: answerRow.getValues()) {
                    if (ans == null) {
                        row.add(null);
                        continue;
                    }
                    QuestionType ansType = ans.getQuestionType();
                    if (ansType == QuestionType.DATE) {
                        DateValue ansValue = (DateValue) ans.getValue();
                        row.add(ansValue == null ? null
                                : String.format("%04d-%02d-%02d", ansValue.getYear(), ansValue.getMonth(), ansValue.getDay()));
                    } else if (ansType == QuestionType.PICKLIST) {
                        List<SelectedPicklistOption> selected = ((PicklistAnswer) ans).getValue();
                        if (selected == null) {
                            row.add(null);
                        } else {
                            selected = new ArrayList<>(selected);
                            selected.sort(Comparator.comparing(SelectedPicklistOption::getStableId));
                            List<String> stableIds = new ArrayList<>();
                            String detailText = null;
                            for (SelectedPicklistOption option : selected) {
                                stableIds.add(option.getStableId());
                                if (option.getDetailText() != null) {
                                    detailText = option.getDetailText();
                                }
                            }
                            String value = String.join(",", stableIds);
                            if (detailText != null) {
                                // Only one detail text for now
                                value = String.join("|", value, detailText);
                            }
                            row.add(value);
                        }
                    } else {
                        row.add(ans.getValue() == null ? null : ans.getValue().toString());
                    }
                }
                this.answer.add(row);
            }
        }
    }
}
