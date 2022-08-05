package org.broadinstitute.dsm.files.parser;

import java.util.Map;

import org.broadinstitute.dsm.util.SystemUtil;

public class RegexSeparatorDictionary {

    private static final Map<String, String> dictionary = Map.of(
            SystemUtil.TAB_SEPARATOR, "tab separator"
    );

    public static String getWordDescription(String key) {
        return dictionary.get(key);
    }

}
