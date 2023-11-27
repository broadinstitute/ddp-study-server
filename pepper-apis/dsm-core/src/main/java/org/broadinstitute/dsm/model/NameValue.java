package org.broadinstitute.dsm.model;

import lombok.Data;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;

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

    public String getCamelCaseFieldName() {
        return CamelCaseConverter.of(Util.getDBElement(this.name).getColumnName()).convert();
    }

    @Override
    public String toString() {
        return "NameValue[name:" + name + ", value:" + value + "]";
    }
}
