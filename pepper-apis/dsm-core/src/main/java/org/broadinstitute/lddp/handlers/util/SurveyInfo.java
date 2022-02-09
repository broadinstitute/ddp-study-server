package org.broadinstitute.lddp.handlers.util;

import lombok.Data;

@Data
public class SurveyInfo {
    private String name;
    private FollowUpType type;

    public SurveyInfo() {
    }

    public SurveyInfo(String name, FollowUpType type) {
        this.name = name;
        this.type = type;
    }

    public enum FollowUpType {
        NONE, REPEATING, NONREPEATING
    }
}
