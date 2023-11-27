
package org.broadinstitute.dsm.model.elastic.retrieve.activities;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.retrieve.activities.osteo.OldOsteoActivityDefinitionsRetriever;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;

public class ActivityDefinitionsRetrieverFactory {

    private final DDPInstance ddpInstance;

    public ActivityDefinitionsRetrieverFactory(DDPInstance ddpInstance) {
        this.ddpInstance = ddpInstance;
    }

    public ActivityDefinitionsRetriever spawn() {
        ActivityDefinitionsRetriever activityDefinitionsRetriever = new ActivityDefinitionsRetriever(ddpInstance);
        if (StudyPostFilter.OLD_OSTEO_INSTANCE_NAME.equals(ddpInstance.getName())) {
            activityDefinitionsRetriever = new OldOsteoActivityDefinitionsRetriever(ddpInstance);
        }
        return activityDefinitionsRetriever;
    }

}
