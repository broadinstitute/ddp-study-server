package org.broadinstitute.ddp.json;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;

public class PatchAnswerResponse {

    @SerializedName("answers")
    private List<AnswerResponse> answers = new ArrayList<>();
    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();
    @SerializedName("validationFailures")
    public List<ActivityValidationFailure> validationFailures;

    public PatchAnswerResponse() {}

    public PatchAnswerResponse(List<ActivityValidationFailure> validationFailures) {
        this.validationFailures = validationFailures;
    }

    public void addAnswer(AnswerResponse answer) {
        this.answers.add(answer);
    }

    public List<AnswerResponse> getAnswers() {
        return answers;
    }

    public List<BlockVisibility> getBlockVisibilities() {
        return blockVisibilities;
    }

    public void setBlockVisibilities(List<BlockVisibility> blockVisibilities) {
        this.blockVisibilities = blockVisibilities;
    }

    public void addValidationFailures(List<ActivityValidationFailure> validationFailures) {
        this.validationFailures = validationFailures;
    }
}
