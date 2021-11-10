package org.broadinstitute.lddp.handlers.util;

import lombok.Data;

@Data
public class SurveyInfo
{
    public enum FollowUpType
    {
        NONE, REPEATING, NONREPEATING
    }

    public SurveyInfo() {
    }

    public SurveyInfo(String name, FollowUpType type) {
        this.name = name;
        this.type = type;
    }

    private String name;
    private FollowUpType type;
}
