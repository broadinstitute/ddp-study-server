package org.broadinstitute.ddp.model.dsm;

import java.util.Objects;

public class Drug {
    private String name;
    private String description;

    public Drug(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Drug)) {
            return false;
        }
        Drug drug = (Drug) o;
        if (description != null) {
            return drug.name.equals(name) && drug.description.equals(description);
        } else {
            return drug.name.equals(name);
        }
    }
}
