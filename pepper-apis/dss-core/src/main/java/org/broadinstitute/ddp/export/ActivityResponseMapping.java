package org.broadinstitute.ddp.export;

import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;

public class ActivityResponseMapping {
    private String activityCode;
    private List<ActivityResponse> responses;

    public ActivityResponseMapping(String activityCode,
                                   List<ActivityResponse> responses) {
        this.activityCode = activityCode;
        this.responses = responses;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public List<ActivityResponse> getResponses() {
        return responses;
    }
}
