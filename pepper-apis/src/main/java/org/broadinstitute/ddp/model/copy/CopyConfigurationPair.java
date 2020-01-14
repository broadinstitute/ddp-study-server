package org.broadinstitute.ddp.model.copy;

import java.util.ArrayList;
import java.util.List;

public class CopyConfigurationPair {

    private long id;
    private CopyLocation source;
    private CopyLocation target;
    private List<CompositeCopyConfigurationPair> compositeChildLocations = new ArrayList<>();
    private int order;

    public CopyConfigurationPair(long id, CopyLocation source, CopyLocation target, int order) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.order = order;
    }

    public CopyConfigurationPair(CopyLocation source, CopyLocation target, List<CompositeCopyConfigurationPair> compositeChildLocations) {
        this.source = source;
        this.target = target;
        addCompositeChildLocations(compositeChildLocations);
    }

    public long getId() {
        return id;
    }

    public CopyLocation getSource() {
        return source;
    }

    public CopyLocation getTarget() {
        return target;
    }

    public List<CompositeCopyConfigurationPair> getCompositeChildLocations() {
        return List.copyOf(compositeChildLocations);
    }

    public void addCompositeChildLocations(List<CompositeCopyConfigurationPair> compositeChildLocations) {
        if (compositeChildLocations != null) {
            this.compositeChildLocations.addAll(compositeChildLocations);
        }
    }

    public int getOrder() {
        return order;
    }

    public CopyConfigurationPair setOrder(int order) {
        this.order = order;
        return this;
    }
}
