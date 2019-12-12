package org.broadinstitute.ddp.export.json.structured;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class PicklistQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<String> selected = new ArrayList<>();
    @SerializedName("optionDetails")
    private List<Map<String, String>> optionDetails = new ArrayList<>();

    public PicklistQuestionRecord(String stableId, List<SelectedPicklistOption> answer) {
        super(QuestionType.PICKLIST, stableId);
        if (CollectionUtils.isNotEmpty(answer)) {
            List<SelectedPicklistOption> selected = new ArrayList<>(answer);
            selected.sort(Comparator.comparing(SelectedPicklistOption::getStableId));
            for (SelectedPicklistOption option : selected) {
                this.selected.add(option.getStableId());
                if (option.getDetailText() != null) {
                    Map<String, String> details = new LinkedHashMap<>();
                    details.put("option", option.getStableId());
                    details.put("details", option.getDetailText());
                    this.optionDetails.add(details);
                }
            }
        }
    }
}
