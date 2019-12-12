package org.broadinstitute.ddp.model.activity.instance.answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public class CompositeAnswer extends Answer<List<AnswerRow>> {

    @Valid
    @NotNull
    @Size(min = 1)
    @SerializedName("value")
    private List<AnswerRow> values;

    private transient boolean allowMultiple;
    private transient boolean unwrapOnExport;

    public CompositeAnswer(Long answerId, String questionStableId, String answerGuid) {
        super(QuestionType.COMPOSITE, answerId, questionStableId, answerGuid);
        this.values = new ArrayList<>();
    }

    @Override
    public List<AnswerRow> getValue() {
        return values;
    }

    @Override
    public void setValue(List<AnswerRow> value) {
        this.values = value;
    }

    public void addRowOfChildAnswers(List<Answer> valuesInSingleRow) {
        values.add(new AnswerRow(valuesInSingleRow));
    }

    public void addRowOfChildAnswers(Answer<?>... valuesInSingleRow) {
        values.add(new AnswerRow(Arrays.asList(valuesInSingleRow)));
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public boolean isUnwrapOnExport() {
        return unwrapOnExport;
    }

    public void setUnwrapOnExport(boolean unwrapOnExport) {
        this.unwrapOnExport = unwrapOnExport;
    }

    public boolean shouldUnwrapChildAnswers() {
        return unwrapOnExport && !allowMultiple;
    }
}
