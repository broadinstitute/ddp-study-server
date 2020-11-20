package org.broadinstitute.ddp.export.json.structured;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class PicklistQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<String> selected = new ArrayList<>();
    @SerializedName("optionDetails")
    private List<Map<String, String>> optionDetails = new ArrayList<>();
    @SerializedName("nestedOptions")
    private Map<String, List<String>> nestedOptions = new HashMap<>();

    public PicklistQuestionRecord(String stableId, List<SelectedPicklistOption> answer) {
        super(QuestionType.PICKLIST, stableId);
        if (CollectionUtils.isNotEmpty(answer)) {
            List<SelectedPicklistOption> selected = new ArrayList<>(answer);
            selected.sort(Comparator.comparing(SelectedPicklistOption::getStableId));
            for (SelectedPicklistOption option : selected) {
                if (StringUtils.isNotBlank(option.getParentStableId())) {
                    String parentStableId = option.getParentStableId();
                    nestedOptions.computeIfAbsent(parentStableId, id -> new ArrayList<>()).add(option.getStableId());
                } else {
                    this.selected.add(option.getStableId());
                }
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
