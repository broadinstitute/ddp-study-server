
package org.broadinstitute.dsm.model.elastic.retrieve.activities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.retrieve.ElasticSearchObjectsRetriever;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class ActivityDefinitionsRetriever implements ElasticSearchObjectsRetriever<Map<String, Map<String, Object>>> {

    private final DDPInstance instance;

    public ActivityDefinitionsRetriever(DDPInstance instance) {
        this.instance = instance;
    }

    @Override
    public Map<String, Map<String, Object>> retrieve() {
        Map<String, Map<String, Object>> activityDefinitions =
                new ConcurrentHashMap<>(ElasticSearchUtil.getActivityDefinitions(instance));
        return applyFilterIfRequired(activityDefinitions);
    }

    protected Map<String, Map<String, Object>> applyFilterIfRequired(Map<String, Map<String, Object>> activityDefinitions) {
        return activityDefinitions;
    }

}
