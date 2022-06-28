package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class PickListValueProvider implements ValueProvider {

    @Override
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap, key, column.getParticipantColumn());
        List<NameValue> options = column.getOptions();
        if (options == null) {
            return nestedValue.stream().map(Object::toString).collect(Collectors.toList());
        }

        if (ESObjectConstants.PARTICIPANT_DATA.equals(column.getParticipantColumn().getTableAlias())) {
            return nestedValue.stream().map(val -> options.stream()
                            .filter(nameValue -> nameValue.getValue().equals(val))
                            .map(NameValue::getName).findFirst().orElse(val.toString()))
                    .collect(Collectors.toList());
        }
        return nestedValue.stream().map(val -> options.stream()
                        .filter(nameValue -> nameValue.getName().equals(val))
                        .map(NameValue::getValue).findFirst().orElse(val).toString())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<?> getNestedValue(String fieldName, Map<String, Object> esDataAsMap,
                                        Alias key, ParticipantColumn participantColumn) {
        return ValueProvider.super.getNestedValue(fieldName, esDataAsMap, key, participantColumn);
    }
}
