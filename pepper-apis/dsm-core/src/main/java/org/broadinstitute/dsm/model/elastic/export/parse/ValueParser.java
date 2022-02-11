package org.broadinstitute.dsm.model.elastic.export.parse;

import org.apache.commons.lang3.StringUtils;

public class ValueParser extends BaseParser {

    @Override
    protected Object forNumeric(String value) {
        return Long.valueOf(value);
    }

    @Override
    protected Object forBoolean(String value) { return Boolean.valueOf(value); }

    @Override
    protected Object forDate(String value) {
        if (StringUtils.isBlank(value)) return null;
        return value;
    }

    @Override
    protected Object forString(String value) {
        return value;
    }
}
