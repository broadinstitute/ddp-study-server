package org.broadinstitute.ddp.model.dsm;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;

public class Cancer {
    @SerializedName("name")
    private String name;
    @SerializedName("description")
    private String description;

    public Cancer(String name, String description) {
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
        if (!(o instanceof Cancer)) {
            return false;
        }
        Cancer drug = (Cancer) o;
        if (description != null) {
            return drug.name.equals(name) && drug.description.equals(description);
        } else {
            return drug.name.equals(name);
        }
    }
}
