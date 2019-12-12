package org.broadinstitute.ddp.model.dsm;

import java.beans.ConstructorProperties;

/**
 * Represent the available kit types.
 */
public class KitType {
    private final long id;
    private final String name;

    @ConstructorProperties({"kit_type_id", "name"})
    public KitType(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

}

