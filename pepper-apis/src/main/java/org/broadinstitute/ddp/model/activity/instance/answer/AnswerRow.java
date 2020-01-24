package org.broadinstitute.ddp.model.activity.instance.answer;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;

import com.google.gson.annotations.JsonAdapter;
import org.broadinstitute.ddp.transformers.AnswerRowAdapter;

@JsonAdapter(AnswerRowAdapter.class)
public class AnswerRow {
    @Valid
    private List<Answer> values;

    public AnswerRow() {
        this.values = new ArrayList<>();
    }

    public AnswerRow(List<Answer> rowOfAnswers) {
        this.values = new ArrayList<>(rowOfAnswers);
    }

    public List<Answer> getValues() {
        return values;
    }
}
