
package org.broadinstitute.dsm.model.elastic.retrieve.activities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.retrieve.ElasticSearchObjectsRetriever;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;

public class ActivityDefinitionsRetriever implements ElasticSearchObjectsRetriever<Map<String, Map<String, Object>>> {

    private final DDPInstance instance;
    private final ElasticSearchable elasticSearchable;

    public ActivityDefinitionsRetriever(DDPInstance instance) {
        this.instance          = instance;
        this.elasticSearchable = new ElasticSearch();
    }

    @Override
    public Map<String, Map<String, Object>> retrieve() {
        return applyFilterIfRequired(new ConcurrentHashMap<>(elasticSearchable.getActivityDefinitions(instance)));
    }

    protected Map<String, Map<String, Object>> applyFilterIfRequired(Map<String, Map<String, Object>> activityDefinitions) {
        return activityDefinitions;
    }

}
