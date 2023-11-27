package org.broadinstitute.dsm.model.elastic.converters;

import java.util.Map;

public abstract class BaseConverter implements Converter<Map<String, Object>> {

    protected String fieldName;
    protected Object fieldValue;
    protected String realm;

    BaseConverter() {
    }
}
