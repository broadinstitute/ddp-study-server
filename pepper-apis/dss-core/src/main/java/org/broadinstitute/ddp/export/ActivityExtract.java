package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;

/**
 * A data extract that encapsulates the definition of the activity and it's version, plus other associated data.
 */
public class ActivityExtract {

    private ActivityDef definition;
    private ActivityVersionDto versionDto;
    private Integer maxInstancesSeen;
    private List<String> attributesSeen = new ArrayList<>();

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

    public Integer getMaxInstancesSeen() {
        return maxInstancesSeen;
    }

    public void setMaxInstancesSeen(Integer maxInstancesSeen) {
        this.maxInstancesSeen = maxInstancesSeen;
    }

    public List<String> getAttributesSeen() {
        return attributesSeen;
    }

    public void addAttributesSeen(List<String> attributesSeen) {
        if (attributesSeen != null) {
            this.attributesSeen.addAll(attributesSeen);
        }
    }
}
