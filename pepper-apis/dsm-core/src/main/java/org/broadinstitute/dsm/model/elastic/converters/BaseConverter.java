package org.broadinstitute.dsm.model.elastic.converters;

public abstract class BaseConverter implements Converter {

    protected String fieldName;
    protected Object fieldValue;
    protected String realm;

    BaseConverter() {
    }
}
