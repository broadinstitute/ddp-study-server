package org.broadinstitute.ddp.customexport.export;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;

public class CustomActivityExtract extends ActivityExtract {
    private Map<String, List<CustomActivityExtract>> childExtracts;
    private final boolean canHaveMultiple;

    public CustomActivityExtract(ActivityDef definition, ActivityVersionDto versionDto, boolean canHaveMultiple) {
        super(definition, versionDto);
        this.canHaveMultiple = canHaveMultiple;
    }

    public CustomActivityExtract(ActivityDef definition, ActivityVersionDto versionDto,
                                 Map<String, List<CustomActivityExtract>> childExtracts, boolean canHaveMultiple) {
        super(definition, versionDto);
        this.childExtracts = childExtracts;
        this.canHaveMultiple = canHaveMultiple;
    }

    public Map<String, List<CustomActivityExtract>> getChildExtracts() {
        return childExtracts;
    }

    public boolean getCanHaveMultiple() {
        return canHaveMultiple;
    }

    public List<String> getAttributesSeen(List<String> firstFields, List<String> excluded) {
        List<String> attributesSeen = super.getAttributesSeen();

        if (firstFields != null && !firstFields.isEmpty()) {
            attributesSeen.removeAll(firstFields);
            attributesSeen.addAll(0, firstFields);
        }

        if (excluded != null && !excluded.isEmpty()) {
            attributesSeen.removeAll(excluded);
        }

        return attributesSeen;
    }
}
