package org.broadinstitute.dsm.model.elastic.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;

//class to separate 'AND' or 'OR' conditions into type of Map<String, List<String>>
public class AndOrFilterSeparator {

    private static final String AND_OPERATOR = "AND";
    private static final String OR_OPERATOR = "OR";
    private static final String AND_MATCHER = "(^|\\s)(AND)\\s";
    private static final String OR_MATCHER = "(^|\\s)(OR)\\s";
    private static final String GENERAL_REGEX = "([A-z]|\\W|\\w)+";
    protected static String orDsmAliasRegex = OR_MATCHER + GENERAL_REGEX;
    protected static String andDsmAliasRegex = AND_MATCHER + GENERAL_REGEX;
    protected static final int AND_PATTERN_MATCHER_NUMBER = 7;
    protected static final int OR_PATTERN_MATCHER_NUMBER = 6;
    public static final int MINIMUM_STEP_FROM_OPERATOR = 3;
    private final Pattern orMatcherPattern;
    private final Pattern andMatcherPattern;
    private String filter;

    public AndOrFilterSeparator(String filter) {
        this.filter = filter;
        this.andMatcherPattern = Pattern.compile(AND_MATCHER);
        this.orMatcherPattern = Pattern.compile(OR_MATCHER);
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Map<String, List<String>> parseFiltersByLogicalOperators() {
        Map<String, List<String>> filterByLogicalOperators =
                new ConcurrentHashMap<>(Map.of(AND_OPERATOR, new ArrayList<>(), OR_OPERATOR, new ArrayList<>()));
        int andIndex = getOperatorIndex(AND_OPERATOR, 0);
        int orIndex = getOperatorIndex(OR_OPERATOR, 0);
        while (andIndex != -1 || orIndex != -1) {
            andIndex =
                    findProperOperatorSplitterIndex(AND_OPERATOR, andIndex, AND_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);
            orIndex = findProperOperatorSplitterIndex(OR_OPERATOR, orIndex, OR_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);
            if (andIndex != -1) {
                andIndex = getIndex(filterByLogicalOperators, andIndex, AND_OPERATOR);
            } else if (orIndex != -1) {
                orIndex = getIndex(filterByLogicalOperators, orIndex, OR_OPERATOR);
            }
        }
        handleSpecialCases(filterByLogicalOperators);
        return filterByLogicalOperators;
    }

    /**
     * "AND ( oD.request = 'review' OR oD.request = 'no' ) " - filter
     *
     * @param operator               -"AND", "OR"
     * @param startIndex             - index where first operator is found
     * @param patternMatcherNumber   - number to add start index to find out whether it matches alias regex | "AND ( o"
     * @param nextOperatorFromNumber - number to add start index to find next operator index | OR oD. index of OR
     * @return index of next proper operator after first operator, proper operator matches either AND_DSM_ALIAS_REGEX or OR_DSM_ALIAS_REGEX
     */
    private int findProperOperatorSplitterIndex(String operator, int startIndex, int patternMatcherNumber, int nextOperatorFromNumber) {
        String aliasRegex = getAliasRegexByOperator(operator);
        while (startIndex != -1
                && (isOperatorWrappedInParenthesis(startIndex)
                || !isMatches(startIndex, startIndex + patternMatcherNumber, aliasRegex))) {
            startIndex = findNextOperatorIndex(operator, startIndex + nextOperatorFromNumber);
        }
        return startIndex;
    }

    private int getIndex(Map<String, List<String>> filterByLogicalOperators, int index, String operator) {

        int orPrecedeIndex = findNextOperatorIndex(OR_OPERATOR, index + MINIMUM_STEP_FROM_OPERATOR);
        orPrecedeIndex =
                findProperOperatorSplitterIndex(OR_OPERATOR, orPrecedeIndex, OR_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);

        int andPrecedeIndex = findNextOperatorIndex(AND_OPERATOR, index + MINIMUM_STEP_FROM_OPERATOR);
        andPrecedeIndex = findProperOperatorSplitterIndex(AND_OPERATOR, andPrecedeIndex, AND_PATTERN_MATCHER_NUMBER,
                MINIMUM_STEP_FROM_OPERATOR);

        // index = 5, 4 ,20
        if (isAndOrGreaterThanCurrentPosition(index, orPrecedeIndex, andPrecedeIndex)) {
            filterByLogicalOperators.get(operator)
                    .add(filter.substring(index + MINIMUM_STEP_FROM_OPERATOR, Math.min(orPrecedeIndex, andPrecedeIndex)).trim());
            index = Math.min(orPrecedeIndex, andPrecedeIndex);
        } else {
            if (isEndOfFilter(orPrecedeIndex, andPrecedeIndex)) {
                filterByLogicalOperators.get(operator).add(filter.substring(index + MINIMUM_STEP_FROM_OPERATOR).trim());
                index = -1;
            } else {
                filterByLogicalOperators.get(operator).add(filter.substring(index + MINIMUM_STEP_FROM_OPERATOR, Math.max(orPrecedeIndex,
                        andPrecedeIndex)).trim());
                index = Math.max(orPrecedeIndex,
                        andPrecedeIndex);
            }
        }
        return index;
    }

    private void handleSpecialCases(Map<String, List<String>> filterByLogicalOperators) {
        final String additionalValuesJsonIsNotNull = "additional_values_json IS NOT NULL";
        for (Map.Entry<String, List<String>> entry : filterByLogicalOperators.entrySet()) {
            List<String> filteredByNotAdditionalValuesIsNotNull = entry.getValue().stream()
                    .filter(f -> !f.contains(additionalValuesJsonIsNotNull))
                    .collect(Collectors.toList());
            filterByLogicalOperators.put(entry.getKey(), filteredByNotAdditionalValuesIsNotNull);
        }
    }

    private String getAliasRegexByOperator(String operator) {
        return AND_OPERATOR.equals(operator) ? andDsmAliasRegex : orDsmAliasRegex;
    }

    private boolean isOperatorWrappedInParenthesis(int startIndex) {
        //PEPPER-508: closing parenthesis from "JSON_EXTRACT (" is seen as the closing parenthesis from the AND (
        boolean exists = false;
        for (int i = startIndex; i > 2; i--) {
            char c = filter.charAt(i);
            if (c == Filter.OPEN_PARENTHESIS_CHAR) {
                exists = true;
                break;
            }
            if (AND_OPERATOR.equals(filter.substring(i - 3, i)) || c == Filter.CLOSE_PARENTHESIS_CHAR) {
                break;
            }
        }
        return exists;
    }

    private int findNextOperatorIndex(String operator, int fromIndex) {
        int index = getOperatorIndex(operator, fromIndex);
        if (isOperatorWrappedInParenthesis(index)) {
            index = filter.indexOf(Filter.CLOSE_PARENTHESIS, index);
            index = getOperatorIndex(operator, index);
        }
        return index;
    }

    private boolean isMatches(int patternStartIndex, int patternEndIndex, String operatorRegex) {
        return patternEndIndex < filter.length() && filter.substring(patternStartIndex, patternEndIndex).matches(operatorRegex);
    }

    private int getOperatorIndex(String operator, int fromIndex) {
        Pattern pattern = operator.equals(AND_OPERATOR) ? andMatcherPattern : orMatcherPattern;
        Matcher matcher = pattern.matcher(filter.substring(fromIndex));
        if (matcher.find()) {
            String token = matcher.group();
            return matcher.start() + fromIndex + token.indexOf(operator);
        }
        return -1;
    }

    private boolean isAndOrGreaterThanCurrentPosition(int index, int orPrecedeIndex, int andPrecedeIndex) {
        return andPrecedeIndex > index && orPrecedeIndex > index;
    }

    private boolean isEndOfFilter(int orPrecedeIndex, int andPrecedeIndex) {
        return andPrecedeIndex == -1 && orPrecedeIndex == -1;
    }

}
