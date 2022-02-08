package org.broadinstitute.ddp.customexport.collectors;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;

public class ParentActivityResponseCollector {
    private final ActivityResponseCollector mainCollector;
    private final Map<String, List<ActivityResponseCollector>> childCollectors;
    private final Map<String, List<ComplexChildResponseCollector>> multiChildExtracts;

    public ParentActivityResponseCollector(ActivityResponseCollector mainCollector,
                                           Map<String, List<ActivityResponseCollector>> childCollectors,
                                           Map<String, List<ComplexChildResponseCollector>> multiChildExtracts) {
        this.mainCollector = mainCollector;
        this.childCollectors = childCollectors;
        this.multiChildExtracts = multiChildExtracts;
    }

    public ActivityResponseCollector getMainCollector() {
        return mainCollector;
    }

    public Map<String, List<ActivityResponseCollector>> getChildCollectors() {
        return childCollectors;
    }

    public Map<String, List<ComplexChildResponseCollector>> getMultiChildCollectors() {
        return multiChildExtracts;
    }
}
