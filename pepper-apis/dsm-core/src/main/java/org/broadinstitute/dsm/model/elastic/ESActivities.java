package org.broadinstitute.dsm.model.elastic;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ESActivities {

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


}
