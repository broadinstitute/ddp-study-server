package org.broadinstitute.dsm.model.elastic.export.parse;


public interface Parser {
    Object parse(String value);
    default Object[] parse(String[] values) {
        throw new UnsupportedOperationException();
    }
    void setFieldName(String fieldName);
}
