package org.broadinstitute.dsm.model.elastic.filter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;

public class FilterParser extends ValueParser {

    // '12313' - str
    //  12312 - number

    @Override
    public Object parse(String value) {
        if (isBoolean(value)) {
            return forBoolean(convertBoolean(value));
        } else if (isNumeric(value)) {
            return forNumber(value);
        } else {
            return convertString(value);
        }
    }

    @Override
    public Object[] parse(String[] values) {
        List<Object> parsedValues = new ArrayList<>();
        for (String value : values) {
            parsedValues.add(parse(value));
        }
        return parsedValues.toArray();
    }

    private boolean isNumeric(String value) {
        return !isWrappedByChar(value) && StringUtils.isNumeric(value);
    }

    @Override
    public Object forNumeric(String value) {
        return value;
    }

    private Object forNumber(String value) {
        return Long.parseLong(value);
    }
}
