package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class ActivityStatusValueProvider extends ValueProvider {

    @Override
    public Collection<String> getFormattedValues(FilterExportConfig qConfig, Map<String, Object> formMap) {
        List<Map<String, Object>> activities = (List<Map<String, Object>>) formMap.get(ElasticSearchUtil.ACTIVITIES);

        return Collections.singletonList(activities.stream()
                .map(activity -> String.format("%s : %s", activity.get(ElasticSearchUtil.ACTIVITY_CODE),
                        activity.get(ElasticSearchUtil.STATUS))).collect(Collectors.joining(", ")));
    };
}
