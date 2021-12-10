package org.broadinstitute.dsm.model;

import lombok.Data;

@Data
public class NameValue {

    private String name;
    private Object value;

    public NameValue(String name, Object value) {
        this.name = name;
        this.value = value;
    }
}
