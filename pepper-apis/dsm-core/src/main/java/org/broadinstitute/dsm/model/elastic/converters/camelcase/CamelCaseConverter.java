package org.broadinstitute.dsm.model.elastic.converters.camelcase;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.converters.Converter;

public class CamelCaseConverter implements Converter<String> {

    private static final CamelCaseConverter instance = new CamelCaseConverter();

    private final int firstElementIndex = 0;
    public static final String UNDERSCORE_SEPARATOR = "_";
    private final Pattern camelCaseRegex = Pattern.compile("(([a-z])+([A-z])+(\\.)*)*");
    protected String stringToConvert;

    protected CamelCaseConverter() {}

    public void setStringToConvert(String stringToConvert) {
        this.stringToConvert = stringToConvert;
    }

    private boolean hasNoUnderscores(String[] splittedWords) {
        return splittedWords.length < 2;
    }

    private String handleAllUppercase(String word) {
        return isCamelCase(word) ? word : word.toLowerCase();
    }

    boolean isCamelCase(String word) {
        return camelCaseRegex.matcher(word).matches();
    }

    private String makeCamelCaseFrom(List<StringBuilder> words) {
        for (int i = firstElementIndex; i < words.size(); i++) {
            StringBuilder word = words.get(i);
            if (isNotFirstWord(i, word)) {
                makeFirstLetterUpperCase(word);
            }
        }
        return String.join(StringUtils.EMPTY, words);
    }

    private boolean isNotFirstWord(int i, StringBuilder word) {
        return i != firstElementIndex && word.length() > firstElementIndex;
    }

    private void makeFirstLetterUpperCase(StringBuilder word) {
        word.replace(firstElementIndex, 1, String.valueOf(word.charAt(firstElementIndex)).toUpperCase());
    }

    private List<StringBuilder> makeWordsLowerCase(String[] splittedWords) {
        return Arrays.stream(splittedWords).map(word -> new StringBuilder(word.toLowerCase())).collect(Collectors.toList());
    }

    @Override
    public String convert() {
        String[] splittedWords = stringToConvert.split(UNDERSCORE_SEPARATOR);
        if (hasNoUnderscores(splittedWords)) {
            return handleAllUppercase(stringToConvert);
        }
        return makeCamelCaseFrom(makeWordsLowerCase(splittedWords));
    }

    public static CamelCaseConverter of(String stringToConvert) {
        instance.stringToConvert = stringToConvert;
        return instance;
    }

    public static CamelCaseConverter of() {
        return instance;
    }
}
