package org.broadinstitute.lddp.handlers.util;

import lombok.NonNull;

public class Institution {
    private String id;
    private String type;

    public Institution() {
    }

    public Institution(@NonNull String id, @NonNull String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public enum InstitutionType {
        INSTITUTION, PHYSICIAN, INITIAL_BIOPSY
    }
}
