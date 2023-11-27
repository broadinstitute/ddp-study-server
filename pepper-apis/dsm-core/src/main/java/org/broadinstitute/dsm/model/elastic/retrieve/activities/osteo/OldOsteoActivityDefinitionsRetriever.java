
package org.broadinstitute.dsm.model.elastic.retrieve.activities.osteo;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.retrieve.activities.ActivityDefinitionsRetriever;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;
import org.broadinstitute.dsm.model.filter.postfilter.osteo.OldOsteoPostFilterStrategy;

public class OldOsteoActivityDefinitionsRetriever extends ActivityDefinitionsRetriever {

    public static final String ACTIVITY_CODE    = "activityCode";
    public static final String ACTIVITY_VERSION = "activityVersion";

    public OldOsteoActivityDefinitionsRetriever(DDPInstance instance) {
        super(instance);
    }

    @Override
    protected Map<String, Map<String, Object>> applyFilterIfRequired(Map<String, Map<String, Object>> activityDefinitions) {
        StudyPostFilterStrategy<Activities> filterStrategy = new OldOsteoPostFilterStrategy();
        activityDefinitions.forEach((activityKey, activityDefinition) -> parseToActivity(activityDefinition).ifPresent(activity -> {
            boolean isOldOsteoActivity = filterStrategy.test(activity);
            if (!isOldOsteoActivity) {
                activityDefinitions.remove(activityKey, activityDefinition);
            }
        }));
        return activityDefinitions;
    }

    private static Optional<Activities> parseToActivity(Map<String, Object> activityDef) {
        Optional<Activities> maybeActivity = Optional.empty();
        if (isActivityFieldsDefined(activityDef)) {
            maybeActivity = Optional.of(new Activities(
                  String.valueOf(activityDef.get(ACTIVITY_CODE)),
                  String.valueOf(activityDef.get(ACTIVITY_VERSION))
          ));
        }
        return maybeActivity;
    }

    private static boolean isActivityFieldsDefined(Map<String, Object> activityDef) {
        return activityDef.containsKey(ACTIVITY_CODE) && activityDef.containsKey(ACTIVITY_VERSION);
    }
}
