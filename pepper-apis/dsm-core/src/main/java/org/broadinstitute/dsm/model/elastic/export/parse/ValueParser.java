package org.broadinstitute.dsm.model.elastic.export.parse;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DsmInternalError;

public class ValueParser extends BaseParser {

    public static final String NULL_AS_STRING = "null";
    public static final String N_A = "N/A";
    public static final String N_A_SYMBOLIC_DATE = "1000-01-01";

    @Override
    public Object forNumeric(String value) {
        if (isEmptyOrNullString(value)) {
            return null;
        }
        if (Double.valueOf(value) % 1 == 0) {
            return Double.valueOf(value).longValue(); // allows for robust parsing of decimals
        } else {
            throw new DsmInternalError(String.format("Could not convert %s to a long", value));
        }
    }

    @Override
    protected Object forBoolean(String value) {
        if (isTrue(value)) {
            return true;
        }
        return Boolean.valueOf(value);
    }

    private boolean isTrue(String value) {
        return "1".equals(value);
    }

    @Override
    protected Object forDate(String value) {
        if (isEmptyOrNullString(value)) {
            //returning null for ES, because ES throws exception if we pass empty string to ES for date fields
            value = null;
        } else if (isDateNotAssigned(value)) {
            value = N_A_SYMBOLIC_DATE;
        }
        return value;
    }

    private boolean isDateNotAssigned(String value) {
        return N_A.equals(value);
    }

    private boolean isEmptyOrNullString(String value) {
        return StringUtils.isBlank(value) || NULL_AS_STRING.equals(value);
    }

    @Override
    protected Object forString(String value) {
        return value;
    }
}
