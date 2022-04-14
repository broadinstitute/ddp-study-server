package org.broadinstitute.ddp.elastic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MappingUtil {

    public static Map<String, Object> newKeywordType() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "keyword");
        return props;
    }

    public static Map<String, Object> newTextType() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "text");
        return props;
    }

    public static Map<String, Object> newBoolType() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "boolean");
        return props;
    }

    public static Map<String, Object> newIntType() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "integer");
        return props;
    }

    public static Map<String, Object> newLongType() {
        Map<String, Object> props = new HashMap<>();
        props.put("type", "long");
        return props;
    }

    public static Map<String, Object> newDateType(String format, boolean acceptMalformed) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", "date");
        props.put("ignore_malformed", acceptMalformed);
        props.put("format", format);
        return props;
    }

    public static String appendISOTimestampFormats(String format) {
        return format + "||strict_date_time||strict_date_time_no_millis||epoch_millis";
    }

    public static String appendISODateFormat(String format) {
        return format + "||strict_date";
    }
}
