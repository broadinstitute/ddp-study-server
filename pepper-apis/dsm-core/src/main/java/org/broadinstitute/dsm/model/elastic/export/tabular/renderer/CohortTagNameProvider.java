package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class CohortTagNameProvider extends TextValueProvider {
    @Override
    public List<?> getRawValues(FilterExportConfig filterConfig, Map<String, Object> formMap) {
        // return either the collection of tags or a list containing one empty map
        return (List<Object>) formMap.getOrDefault(ESObjectConstants.COHORT_TAG,
                Collections.singletonList(Collections.emptyMap()));
    }

    @Override
    public List<String> formatRawValues(List<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {

        return ((List<Map<String, Object>>) rawValues).stream().map(val ->
                (String) val.getOrDefault(ESObjectConstants.COHORT_TAG_NAME, StringUtils.EMPTY)
        ).collect(Collectors.toList());
    }
}
