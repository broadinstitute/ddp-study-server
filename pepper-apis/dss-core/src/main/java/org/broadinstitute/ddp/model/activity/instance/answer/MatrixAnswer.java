package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class MatrixAnswer extends Answer<List<SelectedMatrixCell>> {

    @NotNull
    @SerializedName("value")
    private List<@Valid SelectedMatrixCell> value;

    public MatrixAnswer(Long answerId, String questionStableId, String answerGuid, List<SelectedMatrixCell> options) {
        super(QuestionType.MATRIX, answerId, questionStableId, answerGuid);
        this.value = MiscUtil.checkNonNull(options, "options");
    }

    public MatrixAnswer(Long answerId, String questionStableId, String answerGuid, List<SelectedMatrixCell> options,
                          String actInstanceGuid) {
        super(QuestionType.MATRIX, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = MiscUtil.checkNonNull(options, "options");
    }

    public MatrixAnswer(String questionStableId, String answerGuid) {
        super(QuestionType.MATRIX, null, questionStableId, answerGuid);
        this.value = new ArrayList<>();
    }

    @Override
    public List<SelectedMatrixCell> getValue() {
        return this.value;
    }

    @Override
    public void setValue(List<SelectedMatrixCell> value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

}
