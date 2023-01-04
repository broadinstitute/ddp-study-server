package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public final class MatrixQuestionRecord extends QuestionRecord {

    @SerializedName("matrixGroups")
    private List<String> matrixGroups = new ArrayList<>();
    @SerializedName("matrixSelected")
    private Map<String, String> selected = new HashMap<>();
    @SerializedName("answer")
    private List<String> answer = new ArrayList<>();

    public MatrixQuestionRecord(String stableId, List<SelectedMatrixCell> cells) {
        super(QuestionType.MATRIX, stableId);
        if (CollectionUtils.isNotEmpty(cells)) {
            for (SelectedMatrixCell cell : cells) {
                if (!cell.getRowStableId().isBlank() && !cell.getOptionStableId().isBlank()) {
                    String rowStableId = cell.getRowStableId();
                    String optionStableId = cell.getOptionStableId();
                    selected.put(rowStableId, optionStableId);
                    answer.add(String.join(":", rowStableId, optionStableId));
                }
                if (!cell.getGroupStableId().isBlank()) {
                    matrixGroups.add(cell.getGroupStableId());
                }
            }
        }
    }

    public Map<String, String> getSelected() {
        return selected;
    }

    public List<String> getAnswer() {
        return answer;
    }
}
