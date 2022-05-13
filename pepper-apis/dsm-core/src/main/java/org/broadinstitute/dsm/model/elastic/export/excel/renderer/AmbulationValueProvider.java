package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class AmbulationValueProvider implements ValueProvider {
    private final Map<String, String> valueMappings = Map.of(
            "INDEPENDENTLY", "1",
            "MOST_OF_THE_TIME", "2",
            "WITH_ASSISTANCE", "3",
            "USES_WALKER", "4",
            "WHEELCHAIR_WITHOUT_ASSISTANCE", "5",
            "WHEELCHAIR_WITH_ASSISTANCE", "6");

    @Override
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap, key, column.getParticipantColumn());
        return nestedValue.stream().map(Object::toString)
                .map(value -> valueMappings.getOrDefault(value, StringUtils.EMPTY))
                .collect(Collectors.toList());
    }
}
