package org.broadinstitute.ddp.customexport.collectors;

import java.util.List;

import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;

public class ParentActivityResponseCollector {
    private ActivityResponseCollector mainCollector;
    private List<ActivityResponseCollector> childCollectors;

    public ParentActivityResponseCollector(ActivityResponseCollector mainCollector, List<ActivityResponseCollector> childCollectors) {
        this.mainCollector = mainCollector;
        this.childCollectors = childCollectors;
    }

    public ActivityResponseCollector getMainCollector() {
        return mainCollector;
    }

    public List<ActivityResponseCollector> getChildCollectors() {
        return childCollectors;
    }
}
