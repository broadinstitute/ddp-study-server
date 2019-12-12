package org.broadinstitute.ddp.export;

import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;

/**
 * A data extract that encapsulates the definition of the activity and it's version.
 */
public class ActivityExtract {

    private ActivityDef definition;
    private ActivityVersionDto versionDto;

    public ActivityExtract(ActivityDef definition, ActivityVersionDto versionDto) {
        this.definition = definition;
        this.versionDto = versionDto;
    }

    public ActivityDef getDefinition() {
        return definition;
    }

    public ActivityVersionDto getVersionDto() {
        return versionDto;
    }

    public String getTag() {
        return definition.getTag();
    }
}
