package org.broadinstitute.dsm.model.dashboard;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.NonDsmAndOrFilterSeparator;

public class CountAdditionalFilterStrategy extends AdditionalFilterStrategy {
    public CountAdditionalFilterStrategy(QueryBuildPayload queryBuildPayload) {
        super(queryBuildPayload);
    }

    private List<String> splitConcreteFiltersFromAdditionalFilter() {
        String[] separatedFiltersWithDelimiters =
                queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter()
                        .split("((?<=AND)|(?=AND])|(?<=OR)|(?=OR)|(?<=OR)|(?=AND))");

        List<String> fullFilters = new ArrayList<>();
        for (int i = 0; i < separatedFiltersWithDelimiters.length - 1; i += 2) {
            fullFilters.add(separatedFiltersWithDelimiters[i] + separatedFiltersWithDelimiters[i + 1]);
        }
        return fullFilters;
    }

    private Map<String, List<String>> extractFilters(List<String> fullFilters, Class<? extends AndOrFilterSeparator> separatorClass) {
        return fullFilters.stream()
                .map(filter -> {
                    try {
                        return separatorClass.getConstructor(String.class).newInstance(filter).parseFiltersByLogicalOperators();
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(map -> map.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (prev, curr) -> {
                    prev.addAll(curr);
                    return prev;
                }));
    }

    private Map<String, List<String>> mergeFilters(Map<String, List<String>> nonDsmFilters, Map<String, List<String>> dsmFilters) {
        Map<String, List<String>> result = new HashMap<>(nonDsmFilters);
        result.merge("AND", dsmFilters.get("AND"),
                (prev, curr) -> Stream.concat(prev.stream(), curr.stream()).collect(Collectors.toList()));
        result.merge("OR", dsmFilters.get("OR"),
                (prev, curr) -> Stream.concat(prev.stream(), curr.stream()).collect(Collectors.toList()));
        return result;
    }

    @Override
    protected Map<String, List<String>> getSeparatedFilters() {
        List<String> separatedFilters = splitConcreteFiltersFromAdditionalFilter();
        return mergeFilters(
                extractFilters(separatedFilters, AndOrFilterSeparator.class),
                extractFilters(separatedFilters, NonDsmAndOrFilterSeparator.class)
        );
    }
}
