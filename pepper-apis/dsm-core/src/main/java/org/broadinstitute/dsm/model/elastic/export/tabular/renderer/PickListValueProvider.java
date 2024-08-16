package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class PickListValueProvider extends TextValueProvider {
    @Override
    public List<String> formatRawValues(List<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        if (filterConfig.getOptions() == null || filterConfig.isSplitOptionsIntoColumns() || filterConfig.isStableIdsForOptions()) {
            // return the text as-is (which are stableIds), so they can be matched for the multicolumn format or rendered directly
            return super.formatRawValues(rawValues, filterConfig, formMap);
        }
        // attempt to return the user-visible text, rather than the stableId
        return rawValues.stream().map(val -> {
            Map<String, Object> matchingOption = filterConfig.getOptions().stream().filter(opt -> {
                return val != null && val.equals(opt.get(ESObjectConstants.OPTION_STABLE_ID));
            }).findFirst().orElse(null);
            if (matchingOption != null && matchingOption.get(ESObjectConstants.OPTION_TEXT) != null) {
                return matchingOption.get(ESObjectConstants.OPTION_TEXT).toString();
            }
            return val != null ? val.toString() : StringUtils.EMPTY;
        }).collect(Collectors.toList());
    }

    // adds extra logic to handle grouped options & nested options
    protected Object extractValuesFromAnswer(Map<String, Object> targetAnswer, FilterExportConfig filterConfig) {
        if (targetAnswer == null) {
            return null;
        }
        Object groupedOptions = targetAnswer.get(ESObjectConstants.GROUPED_OPTIONS);
        if (groupedOptions instanceof Map && ((Map) groupedOptions).size() > 0) {
            // groupedOptions is a map of groupName -> array of option choices
            Map<String, List> optGroups = (Map<String, List>) groupedOptions;
            List<?> allValues = (List<String>) optGroups.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
            return allValues;
        }

        Object nestedOptions = targetAnswer.get(ESObjectConstants.NESTED_OPTIONS);
        // nestedOptions is a map of parent option -> array of nested option choices
        if (nestedOptions instanceof Map && ((Map) nestedOptions).size() > 0) {
            Map<String, List> nestedOptionsMap = (Map<String, List>) nestedOptions;
            //get main options selected
            List<String> allValues = (List<String>) super.extractValuesFromAnswer(targetAnswer, filterConfig);
            //get nested options selected for the main options
            List<String> allNestedValues = (List<String>) nestedOptionsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
            allValues.addAll(allNestedValues);
            return allValues;
        }
        return super.extractValuesFromAnswer(targetAnswer, filterConfig);

    }

}
