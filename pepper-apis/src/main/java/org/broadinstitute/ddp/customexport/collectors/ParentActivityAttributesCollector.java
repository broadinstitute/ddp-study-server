package org.broadinstitute.ddp.customexport.collectors;

import java.util.List;

import org.broadinstitute.ddp.export.collectors.ActivityAttributesCollector;

public class ParentActivityAttributesCollector {
    private ActivityAttributesCollector mainCollector;
    private List<ActivityAttributesCollector> childCollectors;

    public ParentActivityAttributesCollector(ActivityAttributesCollector mainCollector, List<ActivityAttributesCollector> childCollectors) {
        this.mainCollector = mainCollector;
        this.childCollectors = childCollectors;
    }

    public ActivityAttributesCollector getMainCollector() {
        return mainCollector;
    }

    public List<ActivityAttributesCollector> getChildCollectors() {
        return childCollectors;
    }
}
