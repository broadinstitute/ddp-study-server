package org.broadinstitute.dsm.model.participant;

import org.broadinstitute.dsm.statics.DBConstants;

public class Util {
    public static boolean isUnderDsmKey(String source) {
        return DBConstants.DDP_PARTICIPANT_ALIAS.equals(source) || DBConstants.DDP_MEDICAL_RECORD_ALIAS.equals(source)
                || DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(source) || DBConstants.DDP_KIT_REQUEST_ALIAS.equals(source)
                || DBConstants.DDP_TISSUE_ALIAS.equals(source) || DBConstants.DDP_ONC_HISTORY_ALIAS.equals(source)
                || DBConstants.DDP_PARTICIPANT_DATA_ALIAS.equals(source) || DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(source)
                || DBConstants.COHORT_ALIAS.equals(source);
    }
}
