package org.broadinstitute.dsm.model.dashboard;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseActivitiesStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.CollectionQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.elasticsearch.index.query.QueryBuilder;

public class CountAdditionalFilterStrategy extends AdditionalFilterStrategy {

    /*
        regex to separate complex filters full of with AND, OR operators, also covers edge case not to separate by AND or OR
        operator, if those operators are used between parenthesis (such case is filter by multiple options question)
     */
    public static final String FILTER_AND_OR_DELIMITER =
            "(?<!\\()((?=\\bOR\\b)|(?<=\\bOR\\b))(?![\\w\\s.='\"]*[)])|" + "(?<!\\()((?=\\bAND\\b)|(?<=\\bAND\\b))(?![\\w\\s.='\"]*[)])";

    public CountAdditionalFilterStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);
    }

    private List<String> splitConcreteFiltersFromAdditionalFilter() {
        String[] separatedFiltersWithDelimiters =
                queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter().split(FILTER_AND_OR_DELIMITER);
        return buildFiltersWithOperators(separatedFiltersWithDelimiters);
    }

    private List<String> buildFiltersWithOperators(String[] separatedFiltersWithDelimiters) {
        List<String> fullFilters = new ArrayList<>();
        for (int i = 0; i < separatedFiltersWithDelimiters.length - 1; i += 2) {
            int operatorIndex = i;
            int filterIndex = i + 1;
            fullFilters.add(separatedFiltersWithDelimiters[operatorIndex] + separatedFiltersWithDelimiters[filterIndex]);
        }
        return fullFilters;
    }

    private Map<String, List<String>> extractFilters(List<String> fullFilters, Class<? extends AndOrFilterSeparator> separatorClass) {
        return fullFilters.stream().map(filter -> {
                    try {
                        return separatorClass.getConstructor(String.class).newInstance(filter).parseFiltersByLogicalOperators();
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }).flatMap(map -> map.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (prev, curr) -> {
                    prev.addAll(curr);
                    return prev;
                }));
    }

    @Override
    protected QueryPayload buildQueryPayload(SplitterStrategy splitterStrategy, String datePeriodField) {
        return new QueryPayload(splitterStrategy.getAlias(), splitterStrategy.getInnerProperty(), splitterStrategy.getAlias(),
                valueParser.parse(splitterStrategy.getValue()), queryBuildPayload.getEsParticipantsIndex());
    }

    @Override
    protected List<QueryBuilder> buildQueries(BuildQueryStrategy queryStrategy) {
        List<QueryBuilder> result = super.buildQueries(queryStrategy);
        if (queryStrategy != null && queryStrategy.getBaseQueryBuilder() != null && queryStrategy.getBaseQueryBuilder().getPayload() != null
                && queryStrategy.getBaseQueryBuilder().getPayload().getAlias() != null
                && Alias.of(queryStrategy.getBaseQueryBuilder().getPayload().getAlias()) == Alias.ACTIVITIES) {
            queryStrategy.getBaseQueryBuilder().getPayload().setPath(Alias.ACTIVITIES.getValue());
            BaseActivitiesStrategy baseActivitiesStrategy =
                    BaseActivitiesStrategy.of(new FilterParser(), queryStrategy.getBaseQueryBuilder().getOperator(),
                            queryStrategy.getBaseQueryBuilder());
            result = baseActivitiesStrategy.build();
        }
        return result;
    }

    @Override
    protected BaseQueryBuilder getBaseQueryBuilder(QueryPayload queryPayload) {
        BaseQueryBuilder result = super.getBaseQueryBuilder(queryPayload);
        if (Alias.of(queryPayload.getAlias()).isCollection()) {
            result = new CollectionQueryBuilder(queryPayload);
        }
        return result;
    }

    @Override
    protected Map<String, List<String>> getSeparatedFilters() {
        List<String> separatedFilters = splitConcreteFiltersFromAdditionalFilter();
        return extractFilters(separatedFilters, AndOrFilterSeparator.class);
    }
}
