package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.I18nTemplateConstants;

public class ActivityAttributesCollector {

    // These are listed alphabetically so we can get consistent sorting in export columns.
    public static final List<String> EXPOSED_ATTRIBUTES = List.of(
            I18nTemplateConstants.Snapshot.KIT_REASON_TYPE,
            I18nTemplateConstants.Snapshot.KIT_REQUEST_ID,
            I18nTemplateConstants.Snapshot.TEST_RESULT_CODE,
            I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED);

    private final List<String> attributesForActivity;

    public ActivityAttributesCollector(List<String> attributesSeenForActivity) {
        // Build the list of attributes for this particular activity by filtering
        // through the "exposed" list, and making sure consistent ordering.
        this.attributesForActivity = new ArrayList<>();
        for (var name : EXPOSED_ATTRIBUTES) {
            if (attributesSeenForActivity.contains(name)) {
                this.attributesForActivity.add(name);
            }
        }
    }

    public List<String> headers() {
        return List.copyOf(attributesForActivity);
    }

    public List<String> emptyRow() {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < attributesForActivity.size(); i++) {
            row.add("");
        }
        return row;
    }

    public List<String> format(Map<String, String> activityInstanceSubstitutions) {
        List<String> row = new ArrayList<>();
        for (var name : attributesForActivity) {
            String value = activityInstanceSubstitutions.get(name);
            if (StringUtils.isNotBlank(value)) {
                row.add(value);
            } else {
                row.add("");
            }
        }
        return row;
    }
}
