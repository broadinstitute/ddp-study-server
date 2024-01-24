package org.broadinstitute.dsm.model.elastic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Activities {

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("createdAt")
    private long createdAt;

    @SerializedName("completedAt")
    private long completedAt;

    @SerializedName("activityVersion")
    private String activityVersion;

    @SerializedName("lastUpdatedAt")
    private long lastUpdatedAt;

    @SerializedName("parentInstanceGuid")
    private String parentInstanceGuid;

    @SerializedName("questionsAnswers")
    private List<Map<String, Object>> questionsAnswers;

    @SerializedName("guid")
    private String guid;

    @SerializedName("attributes")
    private Map<String, Object> attributes;

    @SerializedName("status")
    private String status;

    public Activities(String activityCode, String activityVersion) {
        this.activityCode = activityCode;
        this.activityVersion = activityVersion;
    }

    public Activities() {

    }

    public Optional<Object> getAnswerToQuestion(String question) {
        Optional<Map<String, Object>> maybeQuestionAnswers = this.getQuestionsAnswers().stream()
                .filter(questionAnswer -> questionAnswer.get("stableId").equals(question)).findAny();
        if (maybeQuestionAnswers.isPresent()) {
            Map<String, Object> questionAnswer = maybeQuestionAnswers.get();
            if (questionAnswer.containsKey("answer")) {
                return Optional.ofNullable(questionAnswer.get("answer"));
            }
        }
        return Optional.empty();
    }
}
