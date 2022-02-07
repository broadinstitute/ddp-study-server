package org.broadinstitute.dsm.model.elastic.export.parse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import org.apache.commons.lang3.StringUtils;

public abstract class BaseParser implements Parser {

    @Override
    public Object parse(String value) {
        Object result = forString(value);
        if (StringUtils.isNumeric(value)) {
            result = forNumeric(value);
        } else if (isBoolean(value)) {
            result = forBoolean(value);
        } else if (isDateOrTimeOrDateTime(value)) {
            result = forDate(value);
        }
        return result;
    }

    protected abstract Object forNumeric(String value);

    protected abstract Object forBoolean(String value);

    protected abstract Object forDate(String value);

    protected abstract Object forString(String value);

    boolean isDateOrTimeOrDateTime(String value) {
        return isDate(value) || isTime(value) || isDateTime(value);
    }

    boolean isDateTime(String value) {
        try {
            LocalDateTime.parse(value);
        } catch (DateTimeParseException dtpe) {
            return false;
        }
        return true;
    }

    boolean isTime(String value) {
        try {
            LocalTime.parse(value);
        } catch (DateTimeParseException dtpe) {
            return false;
        }
        return true;
    }

    boolean isDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException dtpe) {
            return false;
        }
        return true;
    }

    boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }
}
