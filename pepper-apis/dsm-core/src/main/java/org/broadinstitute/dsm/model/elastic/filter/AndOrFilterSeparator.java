package org.broadinstitute.dsm.model.elastic.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;

public class AndOrFilterSeparator {

    public static final String DSM_ALIAS_REGEX = "(NO|m|p|r|t|d|o|k|JS|ST|DA|\\()(\\.|\\s)*([a-z]|O|R|T|D|)(\\.)*";
    public static final String OR_DSM_ALIAS_REGEX = "(OR) " + DSM_ALIAS_REGEX;
    public static final String AND_DSM_ALIAS_REGEX = "(AND) " + DSM_ALIAS_REGEX;
    public static final int AND_PATTERN_MATCHER_NUMBER = 7;
    public static final int OR_PATTERN_MATCHER_NUMBER = 6;
    public static final int MINIMUM_STEP_FROM_OPERATOR = 3;

    private String filter;

    public AndOrFilterSeparator(String filter) {
        this.filter = filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Map<String, List<String>> parseFiltersByLogicalOperators() {
        Map<String, List<String>> filterByLogicalOperators = new ConcurrentHashMap<>(Map.of(Filter.AND_TRIMMED, new ArrayList<>(), Filter.OR_TRIMMED, new ArrayList<>()));
        int andIndex = filter.indexOf(Filter.AND_TRIMMED);
        int orIndex = filter.indexOf(Filter.OR_TRIMMED);
        while (andIndex != -1 || orIndex != -1) {
            andIndex = findProperOperatorSplitterIndex(Filter.AND_TRIMMED, andIndex, AND_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);
            orIndex = findProperOperatorSplitterIndex(Filter.OR_TRIMMED, orIndex, OR_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);
            if (andIndex != -1) {
                andIndex = getIndex(filterByLogicalOperators, andIndex, Filter.AND_TRIMMED);
            } else if (orIndex != -1){
                orIndex = getIndex(filterByLogicalOperators, orIndex, Filter.OR_TRIMMED);
            }
        }
        handleSpecialCases(filterByLogicalOperators);
        return filterByLogicalOperators;
    }

    /**
     * "AND ( oD.request = 'review' OR oD.request = 'no' ) " - filter
     *
     * @param operator -"AND", "OR"
     * @param startIndex - index where first operator is found
     * @param patternMatcherNumber - number to add start index to find out whether it matches alias regex | "AND ( o"
     * @param nextOperatorFromNumber - number to add start index to find next operator index | OR oD. index of OR
     * @return index of next proper operator after first operator, proper operator matches either AND_DSM_ALIAS_REGEX or OR_DSM_ALIAS_REGEX
     */
    private int findProperOperatorSplitterIndex(String operator, int startIndex, int patternMatcherNumber, int nextOperatorFromNumber) {
        String aliasRegex = getAliasRegexByOperator(operator);
        while (startIndex != -1
                && (isOperatorWrappedInParenthesis(startIndex)
                || !isMatches(startIndex, startIndex + patternMatcherNumber, aliasRegex))){
            startIndex = findNextOperatorIndex(operator, startIndex + nextOperatorFromNumber);
        }
        return startIndex;
    }

    private int getIndex(Map<String, List<String>> filterByLogicalOperators, int index, String operator) {

        int orPrecedeIndex = findNextOperatorIndex(Filter.OR_TRIMMED, index + MINIMUM_STEP_FROM_OPERATOR);
        orPrecedeIndex = findProperOperatorSplitterIndex(Filter.OR_TRIMMED, orPrecedeIndex, OR_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);

        int andPrecedeIndex = findNextOperatorIndex(Filter.AND_TRIMMED, index + MINIMUM_STEP_FROM_OPERATOR);
        andPrecedeIndex = findProperOperatorSplitterIndex(Filter.AND_TRIMMED, andPrecedeIndex, AND_PATTERN_MATCHER_NUMBER, MINIMUM_STEP_FROM_OPERATOR);

        // index = 5, 4 ,20
        if (isAndOrGreaterThanCurrentPosition(index, orPrecedeIndex, andPrecedeIndex)) {
            filterByLogicalOperators.get(operator).add(filter.substring(index + MINIMUM_STEP_FROM_OPERATOR, Math.min(orPrecedeIndex, andPrecedeIndex)).trim());
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
        for (Map.Entry<String, List<String>> entry: filterByLogicalOperators.entrySet()) {
            List<String> filteredByNotAdditionalValuesIsNotNull = entry.getValue().stream()
                    .filter(f -> !f.contains(additionalValuesJsonIsNotNull))
                    .collect(Collectors.toList());
            filterByLogicalOperators.put(entry.getKey(), filteredByNotAdditionalValuesIsNotNull);
        }
    }

    private String getAliasRegexByOperator(String operator) {
        return Filter.AND_TRIMMED.equals(operator) ? AND_DSM_ALIAS_REGEX : OR_DSM_ALIAS_REGEX;
    }

    private boolean isOperatorWrappedInParenthesis(int startIndex) {
        boolean exists = false;
        for (int i = startIndex; i > 2; i--) {
            char c = filter.charAt(i);
            if (c == Filter.OPEN_PARENTHESIS_CHAR) {
                exists = true;
                break;
            }
            if (Filter.AND_TRIMMED.equals(filter.substring(i - 3, i)) || c == Filter.CLOSE_PARENTHESIS_CHAR) break;
        }
        return exists;
    }

    private int findNextOperatorIndex(String operator, int fromIndex) {
        int index = filter.indexOf(operator, fromIndex);
        if (isOperatorWrappedInParenthesis(index)) {
            index = filter.indexOf(Filter.CLOSE_PARENTHESIS, index);
            index = filter.indexOf(operator, index);
        }
        return index;
    }

    private boolean isMatches(int index, int index1, String operator) {
        return filter.substring(index, index1).matches(operator);
    }

    private boolean isAndOrGreaterThanCurrentPosition(int index, int orPrecedeIndex, int andPrecedeIndex) {
        return andPrecedeIndex > index && orPrecedeIndex > index;
    }

    private boolean isEndOfFilter(int orPrecedeIndex, int andPrecedeIndex) {
        return andPrecedeIndex == -1 && orPrecedeIndex == -1;
    }

}
