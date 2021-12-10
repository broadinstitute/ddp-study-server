package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.model.rgp.AutomaticProbandDataCreator;

public class DefaultableMaker {

    public static Defaultable makeDefaultable(Study study) {
        Defaultable defaultable = (studyGuid, participantId) -> true;
        switch (study) {
            case ATCP:
                break;
            case RGP:
                defaultable = new AutomaticProbandDataCreator();
                break;
            default:
                break;
        }
        return defaultable;
    }

}
