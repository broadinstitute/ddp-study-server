package org.broadinstitute.lddp.handlers.util;

import lombok.NonNull;

public class Institution
{
    public static final String INITIAL_BIOPSY_ID = "1";

    public Institution() {
    }

    public enum InstitutionType
    {
        INSTITUTION, PHYSICIAN, INITIAL_BIOPSY
    }

    private String id;
    private String type;

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
}
