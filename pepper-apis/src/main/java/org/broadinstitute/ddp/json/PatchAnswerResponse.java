package org.broadinstitute.ddp.json;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.form.BlockVisibility;

public class PatchAnswerResponse {

    @SerializedName("answers")
    private List<AnswerResponse> answers = new ArrayList<>();
    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();

    public PatchAnswerResponse() {}

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
}
