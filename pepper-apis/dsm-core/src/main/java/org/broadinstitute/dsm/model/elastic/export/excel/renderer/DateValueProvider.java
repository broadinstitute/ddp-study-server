package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class DateValueProvider implements ValueProvider {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap, key, column.getParticipantColumn());
        return nestedValue.stream().map(value -> {
            if (value == null || value.equals(StringUtils.EMPTY)) {
                return StringUtils.EMPTY;
            }
            if (value instanceof String) {
                return ((String) value);
            }
            long dateLong = Long.parseLong(value.toString());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateLong), ZoneOffset.UTC).format(formatter);
        }).collect(Collectors.toList());
    }

}
