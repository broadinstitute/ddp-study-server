package org.broadinstitute.dsm.model.elastic.export.parse;

import org.apache.commons.lang3.StringUtils;

public class ValueParser extends BaseParser {

    @Override
    protected Object forNumeric(String value) {
        return Long.valueOf(value);
    }

    @Override
    protected Object forBoolean(String value) {
        if (isTrue(value))
            return true;
        return Boolean.valueOf(value);
    }

    private boolean isTrue(String value) {
        return "1".equals(value);
    }

    @Override
    protected Object forDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value;
    }

    @Override
    protected Object forString(String value) {
        return value;
    }
}