package org.broadinstitute.dsm.model.elastic.filter;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.JsonExtractQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.MatchQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.MultipleMatchQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.MustExistsQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.MustNotExistsQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.NonExactMatchQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.RangeGTEQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.RangeLTEQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.DateGreaterSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.DateLowerSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.DateSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.DiamondEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.EqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.GreaterThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.IsNotNullSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.IsNullSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonContainsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonExtractSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LessThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LikeSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.MultipleOptionsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.StrDateSplitterStrategy;

public enum Operator {

    LIKE(Filter.LIKE_TRIMMED, new LikeSplitterStrategy(), new NonExactMatchQueryStrategy()),
    EQUALS(Filter.EQUALS_TRIMMED, new EqualsSplitterStrategy(), new MatchQueryStrategy()),
    GREATER_THAN_EQUALS(Filter.LARGER_EQUALS_TRIMMED, new GreaterThanEqualsSplitterStrategy(), new RangeGTEQueryStrategy()),
    LESS_THAN_EQUALS(Filter.SMALLER_EQUALS_TRIMMED, new LessThanEqualsSplitterStrategy(), new RangeLTEQueryStrategy()),
    IS_NOT_NULL(Filter.IS_NOT_NULL_TRIMMED, new IsNotNullSplitterStrategy(), new MustExistsQueryStrategy()),
    IS_NULL(Filter.IS_NULL_TRIMMED, new IsNullSplitterStrategy(), new MustNotExistsQueryStrategy()),
    DIAMOND_EQUALS(Filter.DIAMOND_EQUALS, new DiamondEqualsSplitterStrategy(), new MatchQueryStrategy()),
    MULTIPLE_OPTIONS(Operator.MULTIPLE_OPTIONS_INDICATOR, new MultipleOptionsSplitterStrategy(), new MultipleMatchQueryStrategy()),
    STR_DATE(Filter.DATE_FORMAT, new StrDateSplitterStrategy(), new MatchQueryStrategy()),
    DATE_GREATER_THAN_EQUALS(Filter.DATE_GREATER, new DateGreaterSplitterStrategy(), new RangeGTEQueryStrategy()),
    DATE_LESS_THAN_EQUALS(Filter.DATE_LESS, new DateLowerSplitterStrategy(), new RangeLTEQueryStrategy()),
    JSON_EXTRACT(Filter.JSON_EXTRACT, new JsonExtractSplitterStrategy(), new JsonExtractQueryStrategy()),
    JSON_CONTAINS(Filter.JSON_CONTAINS, new JsonContainsSplitterStrategy(), new MatchQueryStrategy()),
    DATE(Filter.DATE, new DateSplitterStrategy(), new MatchQueryStrategy());

    public static final String MULTIPLE_OPTIONS_INDICATOR = "()";
    public static final String UNKNOWN_OPERATOR = "Unknown operator";
    public static final List<String> IS_NOT_NULL_LIST = Arrays.asList("IS", "NOT", "NULL");

    private String value;
    private SplitterStrategy splitterStrategy;
    private BuildQueryStrategy queryStrategy;

    Operator(String value, SplitterStrategy splitterStrategy, BuildQueryStrategy queryStrategy) {
        this.value = value;
        this.splitterStrategy = splitterStrategy;
        this.queryStrategy = queryStrategy;
    }

    public SplitterStrategy getSplitterStrategy() {
        return this.splitterStrategy;
    }

    public BuildQueryStrategy getQueryStrategy() {
        return this.queryStrategy;
    }

    public BuildQueryStrategy getQueryStrategy(BuildQueryStrategy... queryStrategy) {
        this.queryStrategy.addAdditionalQueryStrategy(queryStrategy);
        return this.queryStrategy;
    }

    public static Operator getOperator(String value) {
        for (Operator op : Operator.values()) {
            if (op.value.trim().equals(value)) {
                return op;
            }
        }
        return null;
    }

    public static Operator extract(String filter) {
        String[] splittedFilter = filter.split(Filter.SPACE);
        if (isMultipleOptions(splittedFilter)) {
            return MULTIPLE_OPTIONS;
        }
        Optional<String> maybeOperator =
                Arrays.stream(splittedFilter).filter(StringUtils::isNotBlank).map(Operator::handleSpecialCaseOperators)
                        .filter(word -> Arrays.stream(Operator.values()).anyMatch(op -> op.value.equals(word))
                                || Operator.IS_NOT_NULL_LIST.contains(word)).distinct()
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
                case "JSON_EXTRACT":
                    return buildJsonExtractOperator(filter);
                default:
                    return Operator.getOperator(operator);
            }
        } else {
            throw new NoSuchElementException(UNKNOWN_OPERATOR);
        }
    }

    private static Operator buildJsonExtractOperator(String filter) {
        Operator decoratedOperator = Operator.extract(filter.replace(Filter.JSON_EXTRACT, StringUtils.EMPTY));
        JsonExtractSplitterStrategy strategy = (JsonExtractSplitterStrategy) JSON_EXTRACT.splitterStrategy;
        strategy.setDecoratedSplitter(decoratedOperator.splitterStrategy);
        return JSON_EXTRACT;
    }

    private static String handleSpecialCaseOperators(String word) {
        String strOperator = StringUtils.EMPTY;
        for (Operator operator : Operator.values()) {
            int startIndex = word.indexOf(operator.value);
            if (startIndex == -1 && !Operator.IS_NOT_NULL_LIST.contains(word)) {
                continue;
            }
            if (word.contains(Filter.OPEN_PARENTHESIS)) {
                strOperator = word.substring(startIndex, startIndex + operator.value.length());
            } else {
                strOperator = word;
            }
            return strOperator;
        }
        return strOperator;
    }

    private static boolean isMultipleOptions(String[] splittedFilter) {
        splittedFilter = cleanFromEmptySpaces(splittedFilter);
        if (splittedFilter.length == 0) {
            return false;
        }
        String firstElement = splittedFilter[0];
        String lastElement = splittedFilter[splittedFilter.length - 1];
        return (Filter.OPEN_PARENTHESIS.equals(firstElement) && Filter.CLOSE_PARENTHESIS.equals(lastElement)) || (
                firstElement.charAt(0) == Filter.OPEN_PARENTHESIS_CHAR
                        && lastElement.charAt(lastElement.length() - 1) == Filter.CLOSE_PARENTHESIS_CHAR);
    }

    private static String[] cleanFromEmptySpaces(String[] splittedFilter) {
        return Arrays.stream(splittedFilter).filter(StringUtils::isNotBlank).collect(Collectors.toList()).toArray(new String[] {});
    }
}
