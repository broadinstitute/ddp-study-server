package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.Arrays;

import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;

public class OldOsteoPostFilterStrategy implements StudyPostFilterStrategy<Activities> {

    private static final String[] OLD_OSTEO_ACTIVITY_CODES = OldOsteoActivityCode.asStrings();
    private static final String OLD_OSTEO_ACTIVITY_VERSION = "v1";

    @Override
    public boolean test(Activities activities) {
        return Arrays.stream(OLD_OSTEO_ACTIVITY_CODES)
                .anyMatch(activityCode -> activities.getActivityCode().equals(activityCode)
                        && activities.getActivityVersion().equals(OLD_OSTEO_ACTIVITY_VERSION));
    }

}
