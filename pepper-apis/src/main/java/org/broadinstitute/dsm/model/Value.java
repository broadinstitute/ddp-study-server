package org.broadinstitute.dsm.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

//EqualsAndHashCode ensures Collection.contains can be used to check for a Value object with a particular value property
@Getter @EqualsAndHashCode
public class Value{

    private String value;
    private String name;
    private String type;
    private String type2;
    private List<Value> values;

    public Value(String value) {
        this.value = value;
    }

    public Value(String value, String type, String name) {
        this.value = value;
        this.type = type;
        this.name = name;
    }

    public Value(String value, String type, String type2, List<Value> values) {
        this.value = value;
        this.type = type;
        this.type2 = type2;
        this.values = values;
    }
}
