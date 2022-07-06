package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;

public class DateValueProvider extends TextValueProvider {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static String parseDate(Object dateValue) {
        if (dateValue == null || dateValue.equals(StringUtils.EMPTY)) {
            return StringUtils.EMPTY;
        }
        if (dateValue instanceof String) {
            return ((String) dateValue);
        }
        long dateLong = Long.parseLong(dateValue.toString());
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateLong), ZoneOffset.UTC).format(formatter);
    }

    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return rawValues.stream().map(DateValueProvider::parseDate).collect(Collectors.toList());
    }

}
