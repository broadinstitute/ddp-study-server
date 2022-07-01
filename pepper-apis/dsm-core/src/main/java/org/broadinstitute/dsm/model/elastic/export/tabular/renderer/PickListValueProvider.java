package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class PickListValueProvider implements ValueProvider {
    @Override
    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig qConfig, Map<String, Object> formMap) {
        if (qConfig.getOptions() == null || qConfig.isSplitOptionsIntoColumns()) {
            // return the defaults (stableId), so they can be matched for the multicolumn format
            return ValueProvider.super.formatRawValues(rawValues, qConfig, formMap);
        }
        // return the user-visible text, rather than the stableId
        return rawValues.stream().map(val -> {
            Map<String, Object> matchingOption = qConfig.getOptions().stream().filter(opt -> {
                return val != null && val.equals(opt.get("optionStableId"));
            }).findFirst().orElse(null);
            if (matchingOption != null && matchingOption.get("optionText") != null) {
                return matchingOption.get("optionText").toString();
            }
            return val.toString();
        }).collect(Collectors.toList());
    }
}
