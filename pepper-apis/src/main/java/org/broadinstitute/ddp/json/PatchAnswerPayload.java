package org.broadinstitute.ddp.json;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class PatchAnswerPayload {

    public static final String ANSWERS_LIST_KEY = "answers";
    @SerializedName("answers")
    private List<AnswerSubmission> answers;

    public PatchAnswerPayload() {
        this.answers = new ArrayList<>();
    }

    public PatchAnswerPayload(List<AnswerSubmission> answers) {
        this.answers = answers;
    }

    public void addSubmission(AnswerSubmission submission) {
        answers.add(submission);
    }

    public List<AnswerSubmission> getSubmissions() {
        return answers;
    }
}
