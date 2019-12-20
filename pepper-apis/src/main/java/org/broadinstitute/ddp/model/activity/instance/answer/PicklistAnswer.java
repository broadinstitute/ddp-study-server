package org.broadinstitute.ddp.model.activity.instance.answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class PicklistAnswer extends Answer<List<SelectedPicklistOption>> {

    @NotNull
    @SerializedName("value")
    private List<@Valid SelectedPicklistOption> value;

    public PicklistAnswer(Long answerId, String questionStableId, String answerGuid, List<SelectedPicklistOption> options) {
        super(QuestionType.PICKLIST, answerId, questionStableId, answerGuid);
        this.value = MiscUtil.checkNonNull(options, "options");
    }

    public PicklistAnswer(String questionStableId, String answerGuid) {
        super(QuestionType.PICKLIST, null, questionStableId, answerGuid);
        this.value = new ArrayList<>();
    }

    @Override
    public List<SelectedPicklistOption> getValue() {
        return this.value;
    }

    @Override
    public void setValue(List<SelectedPicklistOption> value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PicklistAnswer that = (PicklistAnswer) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
