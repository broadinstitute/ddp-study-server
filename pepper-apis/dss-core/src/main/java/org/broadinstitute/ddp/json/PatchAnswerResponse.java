package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;

import java.util.ArrayList;
import java.util.List;

@Value
@NoArgsConstructor
public class PatchAnswerResponse {
    @SerializedName("answers")
    List<AnswerResponse> answers = new ArrayList<>();

    @SerializedName("equations")
    List<EquationResponse> equations = new ArrayList<>();

    @SerializedName("blockVisibility")
    List<BlockVisibility> blockVisibilities = new ArrayList<>();

    @SerializedName("validationFailures")
    List<ActivityValidationFailure> validationFailures = new ArrayList<>();

    public void addAnswer(final AnswerResponse answer) {
        this.answers.add(answer);
    }

    public void addEquation(final EquationResponse equation) {
        this.equations.add(equation);
    }

    public void setBlockVisibilities(final List<BlockVisibility> blockVisibilities) {
        this.blockVisibilities.clear();
        this.blockVisibilities.addAll(blockVisibilities);
    }

    public void addValidationFailures(final List<ActivityValidationFailure> validationFailures) {
        this.validationFailures.addAll(validationFailures);
    }
}
