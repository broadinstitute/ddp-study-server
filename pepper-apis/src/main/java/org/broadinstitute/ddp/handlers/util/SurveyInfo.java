package org.broadinstitute.ddp.handlers.util;

import lombok.Data;
import org.broadinstitute.ddp.datstat.SurveyConfig;

@Data
public class SurveyInfo
{
    public SurveyInfo() {
    }

    public SurveyInfo(String name, SurveyConfig.FollowUpType type) {
        this.name = name;
        this.type = type;
    }

    private String name;
    private SurveyConfig.FollowUpType type;
}
