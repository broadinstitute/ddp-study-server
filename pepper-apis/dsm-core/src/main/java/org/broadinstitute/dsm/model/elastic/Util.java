package org.broadinstitute.dsm.model.elastic;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.PatchUtil;

public class Util {

    public static final String COMMA_SEPARATOR = ",";
    public static final String DOC = "_doc";
    public static final String ESCAPE_CHARACTER = "\\";
    public static final String FORWARD_SLASH_SEPARATOR = "/";

    public static <A> A orElseNull(Optional<A> optionalValue, A defaultValue) {
        try {
            return optionalValue.get().equals(defaultValue) ? null : optionalValue.get();
        } catch (NoSuchElementException nse) {
            return null;
        }
    }

    public static String getQueryTypeFromId(String id) {
        String type;
        if (ParticipantUtil.isHruid(id)) {
            type = Constants.PROFILE_HRUID;
        } else if (ParticipantUtil.isGuid(id)) {
            type = Constants.PROFILE_GUID;
        } else if (ParticipantUtil.isLegacyAltPid(id)) {
            type = Constants.PROFILE_LEGACYALTPID;
        } else {
            type = Constants.PROFILE_LEGACYSHORTID;
        }
        return type;
    }

    public static DBElement getDBElement(String fieldName) {
        return PatchUtil.getColumnNameMap().get(Objects.requireNonNull(fieldName));
    }

    public static String capitalCamelCaseToLowerCamelCase(String capitalCamelCase) {
        StringBuilder className = new StringBuilder(capitalCamelCase);
        StringBuilder camelCaseClassName = className.replace(0, 1, String.valueOf(className.charAt(0)).toLowerCase());
        return camelCaseClassName.toString();
    }

    public static String spacedLowerCaseToCamelCase(String fieldName) {
        String[] separatedBySpace = fieldName.split(Filter.SPACE);
        Stream<String> mappedToUppercase = Arrays.stream(separatedBySpace).skip(1).map(Util::capitalize);
        String firstWordToLowerCase = separatedBySpace[0].toLowerCase();
        LinkedList<String> mappedWords = new LinkedList<>();
        mappedWords.addFirst(firstWordToLowerCase);
        mappedWords.addAll(mappedToUppercase.collect(Collectors.toList()));
        return String.join(StringUtils.EMPTY, mappedWords);
    }

    private static String capitalize(String word) {
        return word.substring(0, 1)
                .toUpperCase()
                .concat(word.substring(1));
    }


    public static class Constants {
        public static final String PROFILE = "profile";
        public static final String PROFILE_HRUID = PROFILE + ".hruid";
        public static final String PROFILE_GUID = PROFILE + ".guid";
        public static final String PROFILE_LEGACYALTPID = PROFILE + ".legacyAltPid";
        public static final String PROFILE_LEGACYSHORTID = PROFILE + ".legacyShortId";
    }
}
