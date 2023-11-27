package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class ActivityStatusValueProvider extends TextValueProvider {

    @Override
    public List<List<String>> getFormattedValues(FilterExportConfig filterConfig, Map<String, Object> formMap) {
        List<Map<String, Object>> activities = (List<Map<String, Object>>) formMap.get(ElasticSearchUtil.ACTIVITIES);
        if (activities == null) {
            return  Collections.singletonList(Collections.singletonList(StringUtils.EMPTY));
        }
        String activitiesString = activities.stream()
                .map(activity -> String.format("%s : %s", activity.get(ElasticSearchUtil.ACTIVITY_CODE),
                        activity.get(ElasticSearchUtil.STATUS))).collect(Collectors.joining(", "));
        return  Collections.singletonList(Collections.singletonList(activitiesString));
    }

}
