package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;

public class PickListValueProvider extends TextValueProvider {
    @Override
    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        if (filterConfig.getOptions() == null || filterConfig.isSplitOptionsIntoColumns()) {
            // return the defaults (stableId), so they can be matched for the multicolumn format
            return super.formatRawValues(rawValues, filterConfig, formMap);
        }
        // return the user-visible text, rather than the stableId
        return rawValues.stream().map(val -> {
            Map<String, Object> matchingOption = filterConfig.getOptions().stream().filter(opt -> {
                return val != null && val.equals(opt.get("optionStableId"));
            }).findFirst().orElse(null);
            if (matchingOption != null && matchingOption.get("optionText") != null) {
                return matchingOption.get("optionText").toString();
            }
            return val.toString();
        }).collect(Collectors.toList());
    }
}
