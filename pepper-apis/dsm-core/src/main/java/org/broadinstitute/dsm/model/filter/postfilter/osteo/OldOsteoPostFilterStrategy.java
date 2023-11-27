package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;

public class OldOsteoPostFilterStrategy implements StudyPostFilterStrategy<Activities> {

    private static final String OLD_OSTEO_ACTIVITY_VERSION = "v1";

    @Override
    public boolean test(Activities activity) {
        return OldOsteoActivityCode.isOldOsteoActivityCode(activity.getActivityCode())
                && OLD_OSTEO_ACTIVITY_VERSION.equals(activity.getActivityVersion());
    }

}
