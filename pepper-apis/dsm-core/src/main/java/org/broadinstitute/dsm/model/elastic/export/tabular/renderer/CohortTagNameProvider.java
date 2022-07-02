package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CohortTagNameProvider extends ValueProvider {
    @Override
    public Collection<?> getRawValues(FilterExportConfig qConfig, Map<String, Object> formMap) {
        // return either the collection of tags or a list containing one empty map
        return (Collection<?>) formMap.getOrDefault(ESObjectConstants.COHORT_TAG,
                Collections.singletonList(Collections.emptyMap()));
    };

    @Override
    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig qConfig, Map<String, Object> formMap) {

        return ((List<Map<String, Object>>) rawValues).stream().map(val -> {
            return (String) val.getOrDefault(ESObjectConstants.COHORT_TAG_NAME, StringUtils.EMPTY);
        }).collect(Collectors.toList());
    }
}
