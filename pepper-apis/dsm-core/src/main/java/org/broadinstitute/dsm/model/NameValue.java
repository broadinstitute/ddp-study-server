package org.broadinstitute.dsm.model;

import java.beans.ConstructorProperties;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
public class NameValue {

    protected String name;
    protected Object value;

    @JdbiConstructor
    @ConstructorProperties ({ "name", "value" })
    public NameValue(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public NameValue(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public NameValue() {

    }
}
