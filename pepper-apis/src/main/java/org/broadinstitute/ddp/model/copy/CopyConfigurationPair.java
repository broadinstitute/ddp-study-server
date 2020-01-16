package org.broadinstitute.ddp.model.copy;

public class CopyConfigurationPair {

    private long id;
    private CopyLocation source;
    private CopyLocation target;
    private int order;

    public CopyConfigurationPair(long id, CopyLocation source, CopyLocation target, int order) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.order = order;
    }

    public CopyConfigurationPair(CopyLocation source, CopyLocation target) {
        this.source = source;
        this.target = target;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
