package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class CompositeValueProvider implements ValueProvider {
    @Override
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap, key, column.getParticipantColumn());
        return nestedValue.stream().map(val -> {
            if (val instanceof String) {
                return (String) val;
            }
            return String.join(", ", (List) val);
        }).collect(Collectors.toList());
    }
}
