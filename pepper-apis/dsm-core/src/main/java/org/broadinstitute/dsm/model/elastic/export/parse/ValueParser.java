package org.broadinstitute.dsm.model.elastic.export.parse;

import org.apache.commons.lang3.StringUtils;

public class ValueParser extends BaseParser {

    public static final String NULL_AS_STRING = "null";

    @Override
    protected Object forNumeric(String value) {
        if (isEmptyOrNullString(value)) {
            //returning null if numeric value is deleted
            return null;
        }
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
        if (isEmptyOrNullString(value)) {
            //returning null for ES, because ES throws exception if we pass empty string to ES for date fields
            return null;
        }
        return value;
    }

    private boolean isEmptyOrNullString(String value) {
        return StringUtils.isBlank(value) || NULL_AS_STRING.equals(value);
    }

    @Override
    protected Object forString(String value) {
        return value;
    }
}
