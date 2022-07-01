package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class BooleanValueProvider implements ValueProvider {
    private static final String YES = "Yes";
    private static final String NO = "No";

    @Override
    /**
     * Return Yes/No, rather than true/false
     */
    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig qConfig, Map<String, Object> formMap) {

        return rawValues.stream().map(value -> {
            return Boolean.parseBoolean(value.toString()) ? YES : NO;
        }).collect(Collectors.toList());
    }
}
