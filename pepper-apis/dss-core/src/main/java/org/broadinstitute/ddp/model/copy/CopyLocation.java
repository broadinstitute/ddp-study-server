package org.broadinstitute.ddp.model.copy;

public class CopyLocation {

    private long id;
    private CopyLocationType type;

    public CopyLocation(long id, CopyLocationType type) {
        this.id = id;
        this.type = type;
    }

    public CopyLocation(CopyLocationType type) {
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public CopyLocationType getType() {
        return type;
    }
}
