package org.broadinstitute.ddp.model.dsm;

import java.beans.ConstructorProperties;

import com.google.gson.annotations.SerializedName;

public class OnDemandActivity {

    @SerializedName("name")
    private String activityCode;

    @SerializedName("type")
    private RepeatType type;

    private transient Long maxInstancesPerUser;

    @ConstructorProperties({"activity_code", "max_instances_per_user"})
    public OnDemandActivity(String activityCode, Long maxInstancesPerUser) {
        this.activityCode = activityCode;
        this.maxInstancesPerUser = maxInstancesPerUser;
        if (maxInstancesPerUser != null && maxInstancesPerUser <= 1L) {
            type = RepeatType.NONREPEATING;
        } else {
            type = RepeatType.REPEATING;
        }
    }

    public String getActivityCode() {
        return activityCode;
    }

    public RepeatType getType() {
        return type;
    }

    public Long getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public enum RepeatType {
        REPEATING,
        NONREPEATING,
    }
}
