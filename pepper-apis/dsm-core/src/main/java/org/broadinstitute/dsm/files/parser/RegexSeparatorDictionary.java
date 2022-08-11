package org.broadinstitute.dsm.files.parser;

import static org.broadinstitute.dsm.util.SystemUtil.TAB_SEPARATOR;

import java.util.Map;

public class RegexSeparatorDictionary {

    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final String SPACE_SEPARATOR = " ";

    private static final Map<String, String> DICTIONARY = Map.of(
            TAB_SEPARATOR, "tab",
            NEW_LINE_SEPARATOR, "new line",
            SPACE_SEPARATOR, "space"
    );

    public static String describe(String regexSeparator) {
        return DICTIONARY.get(regexSeparator).concat(" separator");
    }

}
