package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;

public class DateValueProvider extends TextValueProvider {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

    private static String parseDate(Object dateValue) {
        if (dateValue == null) {
            return StringUtils.EMPTY;
        }
        if (dateValue instanceof String) {
            return ((String) dateValue);
        }
        try {
            long dateLong = Long.parseLong(dateValue.toString());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateLong), ZoneOffset.UTC).format(formatter);
        } catch (Exception e) {
            return dateValue.toString();
        }
    }

    @Override
    public List<String> formatRawValues(List<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return rawValues.stream().map(DateValueProvider::parseDate).collect(Collectors.toList());
    }

}
