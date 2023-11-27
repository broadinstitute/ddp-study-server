package org.broadinstitute.ddp.model.activity.instance.answer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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

    public PicklistAnswer(Long answerId, String questionStableId, String answerGuid, List<SelectedPicklistOption> options,
                          String actInstanceGuid) {
        super(QuestionType.PICKLIST, answerId, questionStableId, answerGuid, actInstanceGuid);
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
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    public SelectedPicklistOption getFirstPickedOption() {
        return Optional.ofNullable(value)
                .map(List::iterator)
                .map(Iterator::next)
                .orElse(null);
    }
}
