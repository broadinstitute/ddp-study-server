package org.broadinstitute.dsm.model.elastic.filter;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;

public enum Operator {

    LIKE(Filter.LIKE_TRIMMED),
    EQUALS(Filter.EQUALS_TRIMMED),
    GREATER_THAN_EQUALS(Filter.LARGER_EQUALS_TRIMMED),
    LESS_THAN_EQUALS(Filter.SMALLER_EQUALS_TRIMMED),
    IS_NOT_NULL(Filter.IS_NOT_NULL_TRIMMED),
    IS_NULL(Filter.IS_NULL_TRIMMED),
    DIAMOND_EQUALS(Filter.DIAMOND_EQUALS),
    MULTIPLE_OPTIONS(Operator.MULTIPLE_OPTIONS_INDICATOR),
    STR_DATE(Filter.DATE_FORMAT),
    DATE_GREATER_THAN_EQUALS(Filter.DATE_GREATER),
    DATE_LESS_THAN_EQUALS(Filter.DATE_LESS),
    JSON_EXTRACT(Filter.JSON_EXTRACT),
    JSON_CONTAINS(Filter.JSON_CONTAINS),
    DATE(Filter.DATE);

    public static final String MULTIPLE_OPTIONS_INDICATOR = "()";
    public static final String UNKNOWN_OPERATOR = "Unknown operator";
    public static final List<String> IS_NOT_NULL_LIST = Arrays.asList("IS", "NOT",
            "NULL");

    private String value;

    Operator(String value) {
        this.value = value;
    }

    public static Operator getOperator(String value) {
        for (Operator op: Operator.values()) {
            if (op.value.trim().equals(value)) return op;
        }
        throw new IllegalArgumentException(UNKNOWN_OPERATOR);
    }

    public static Operator extract(String filter) {
        String[] splittedFilter = filter.split(Filter.SPACE);
        if (isMultipleOptions(splittedFilter))
            return MULTIPLE_OPTIONS;
        Optional<String> maybeOperator = Arrays.stream(splittedFilter)
                .filter(StringUtils::isNotBlank)
                .map(Operator::handleSpecialCaseOperators)
                .filter(word -> Arrays.stream(Operator.values()).anyMatch(op -> op.value.equals(word)) || Operator.IS_NOT_NULL_LIST.contains(word))
                .distinct()
                .reduce((prev, curr) -> String.join(Filter.SPACE, prev, curr));
        if (maybeOperator.isPresent()) {
            String operator = maybeOperator.get();
            switch (operator) {
                case "STR_TO_DATE =":
                    return Operator.STR_DATE;
                case "STR_TO_DATE <=":
                    return Operator.DATE_LESS_THAN_EQUALS;
                case "STR_TO_DATE >=":
                    return Operator.DATE_GREATER_THAN_EQUALS;
                case "DATE =":
                    return Operator.DATE;
                case "NOT <=>":
                    return Operator.DIAMOND_EQUALS;
                case "JSON_CONTAINS":
                    return Operator.JSON_CONTAINS;
                case "JSON_EXTRACT =":
                case "JSON_EXTRACT >=":
                case "JSON_EXTRACT <=":
                case "JSON_EXTRACT LIKE":
                case "JSON_EXTRACT IS NOT NULL":
                case "JSON_EXTRACT IS NULL":
                    return JSON_EXTRACT;
                default:
                    return Operator.getOperator(operator);
            }
        } else {
            throw new NoSuchElementException(UNKNOWN_OPERATOR);
        }
    }

    private static String handleSpecialCaseOperators(String word) {
        String strOperator = StringUtils.EMPTY;
        for (Operator operator : Operator.values()) {
            int startIndex = word.indexOf(operator.value);
            if (startIndex == -1 && !Operator.IS_NOT_NULL_LIST.contains(word)) continue;
            if (word.contains(Filter.OPEN_PARENTHESIS)) strOperator = word.substring(startIndex, startIndex + operator.value.length());
            else strOperator = word;
            return strOperator;
        }
        return strOperator;
    }

    private static boolean isMultipleOptions(String[] splittedFilter) {
        splittedFilter = cleanFromEmptySpaces(splittedFilter);
        if (splittedFilter.length == 0) return false;
        String firstElement = splittedFilter[0];
        String lastElement = splittedFilter[splittedFilter.length - 1];
        return (Filter.OPEN_PARENTHESIS.equals(firstElement) && Filter.CLOSE_PARENTHESIS.equals(lastElement))
                || (firstElement.charAt(0) == Filter.OPEN_PARENTHESIS_CHAR && lastElement.charAt(lastElement.length()-1) ==
                Filter.CLOSE_PARENTHESIS_CHAR);
    }

    private static String[] cleanFromEmptySpaces(String[] splittedFilter) {
        return Arrays.stream(splittedFilter)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList())
                .toArray(new String[] {});
    }
}
