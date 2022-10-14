package org.broadinstitute.dsm.model;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.broadinstitute.dsm.db.FieldSettings;

//EqualsAndHashCode ensures Collection.contains can be used to check for a Value object with a particular value property
@Getter
@EqualsAndHashCode
public class Value {

    private String value;
    private String name;
    private String type;
    private String type2;
    private List<Value> values;
    private FieldSettings conditionalFieldSetting;
    private String condition;

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
