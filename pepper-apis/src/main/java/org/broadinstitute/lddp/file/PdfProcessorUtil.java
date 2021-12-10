package org.broadinstitute.lddp.file;

import java.util.Map;

public class PdfProcessorUtil {


    public static final String YES_APPEND = "_YES";
    public static final String NO_APPEND = "_NO";

    public static final String YES = "Yes";

    public static void setYesNoValue(boolean b, String fieldName, Map<String, Object> map) {
        if (b) {
            map.put(fieldName + YES_APPEND, YES);
        }
        else {
            map.put(fieldName + NO_APPEND, YES);
        }
    }
}
