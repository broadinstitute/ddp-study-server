package org.broadinstitute.ddp.model.copy;

public class CopyPreviousInstanceFilter {

    private long id;
    private CopyAnswerLocation location;
    private int order;

    public CopyPreviousInstanceFilter(long id, CopyAnswerLocation location, int order) {
        this.id = id;
        this.location = location;
        this.order = order;
    }

    public CopyPreviousInstanceFilter(CopyAnswerLocation location) {
        this.location = location;
    }

    public long getId() {
        return id;
    }

    public CopyAnswerLocation getLocation() {
        return location;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
