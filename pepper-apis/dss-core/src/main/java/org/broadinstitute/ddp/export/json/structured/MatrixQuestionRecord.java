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

    public MatrixQuestionRecord(String stableId, List<SelectedMatrixCell> cells) {
        super(QuestionType.MATRIX, stableId);
        if (CollectionUtils.isNotEmpty(cells)) {
            for (SelectedMatrixCell cell : cells) {
                if (!cell.getRowStableId().isBlank() && !cell.getOptionStableId().isBlank()) {
                    selected.put(cell.getRowStableId(), cell.getOptionStableId());
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
}
