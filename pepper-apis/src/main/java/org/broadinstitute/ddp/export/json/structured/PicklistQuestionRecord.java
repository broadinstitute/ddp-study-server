package org.broadinstitute.ddp.export.json.structured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class PicklistQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<String> selected = new ArrayList<>();
    @SerializedName("optionDetails")
    private List<Map<String, String>> optionDetails = new ArrayList<>();
    @SerializedName("picklistHistory")
    private List<Map<String, Object>> history;

    public PicklistQuestionRecord(String stableId, List<SelectedPicklistOption> answer, List<Answer> history) {
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
        this.history = history.stream()
                .map(ans -> {
                    List<SelectedPicklistOption> selected = new ArrayList<>(((PicklistAnswer) ans).getValue());
                    selected.sort(Comparator.comparing(SelectedPicklistOption::getStableId));
                    List<Map<String, String>> optionDetails = new ArrayList<>();
                    for (SelectedPicklistOption option : selected) {
                        if (option.getDetailText() != null) {
                            Map<String, String> details = new LinkedHashMap<>();
                            details.put("option", option.getStableId());
                            details.put("details", option.getDetailText());
                            optionDetails.add(details);
                        }
                    }
                    Map<String, Object> hist = new HashMap<>();
                    hist.put("answer", selected.stream().map(SelectedPicklistOption::getStableId).collect(Collectors.toList()));
                    hist.put("optionDetails", optionDetails);
                    hist.put("updatedAt", Instant.ofEpochMilli(ans.getUpdatedAt()).toString());
                    return hist;
                })
                .collect(Collectors.toList());
    }
}
