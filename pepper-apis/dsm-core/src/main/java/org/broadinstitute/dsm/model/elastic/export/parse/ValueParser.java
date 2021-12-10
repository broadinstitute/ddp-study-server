package org.broadinstitute.dsm.model.elastic.export.parse;

public class ValueParser extends BaseParser {

    @Override
    protected Object forNumeric(String value) {
        return Long.valueOf(value);
    }

    @Override
    protected Object forBoolean(String value) {
        return Boolean.valueOf(value);
    }

    @Override
    protected Object forDate(String value) {
        return value;
    }

    @Override
    protected Object forString(String value) {
        return value;
    }
}
