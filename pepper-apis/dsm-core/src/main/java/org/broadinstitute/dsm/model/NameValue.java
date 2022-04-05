package org.broadinstitute.dsm.model;

import lombok.Data;

@Data
public class NameValue {

    protected String name;
    protected Object value;

    public NameValue(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public NameValue() {

    }
}
