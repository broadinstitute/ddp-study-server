package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class JsonArrayValueProvider implements ValueProvider {
    @Override
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap, key, column.getParticipantColumn());
        return nestedValue.stream().map(Object::toString).collect(Collectors.toList());
    }
}
