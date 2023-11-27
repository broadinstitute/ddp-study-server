package org.broadinstitute.dsm.model.elastic.export.parse;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DashboardValueParser extends ValueParser {


    @Override
    public Object parse(String element) {
        Object result;

        if (isNumeric(element)) {
            result = forNumeric(element);
        } else if (isBoolean(element)) {
            result = forBoolean(element);
        } else if (isDate(element)) {
            result = forDate(element);
        } else {
            result = forString(element);
        }
        return result;
    }

    @Override
    public Object[] parse(String[] values) {
        List<Object> result = new ArrayList<>();
        for (String value : values) {
            result.add(parse(value));
        }
        return result.toArray();
    }

    private boolean isNumeric(String element) {
        boolean result;
        try {
            Long.parseLong(element);
            result = true;
        } catch (NumberFormatException nfe) {
            result = false;
        }
        return result;
    }

    private boolean isDate(String element) {
        boolean result = true;
        try {
            LocalDate.parse(element);
        } catch (DateTimeException de) {
            try {
                LocalDateTime.parse(element);
            } catch (DateTimeException dte) {
                result = false;
            }
        }
        return result;
    }

    @Override
    protected Object forString(String value) {
        return convertString(value);
    }
}
