package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class ActivityStatusValueProvider implements ValueProvider {
    @Override
    public Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column) {
        List<Map<String, Object>> activities = (List<Map<String, Object>>) esDataAsMap.get(ElasticSearchUtil.ACTIVITIES);

        return Collections.singletonList(activities.stream()
                .map(activity -> String.format("%s : %s", activity.get(ElasticSearchUtil.ACTIVITY_CODE),
                        activity.get(ElasticSearchUtil.STATUS))).collect(Collectors.joining(", ")));
    }
}
