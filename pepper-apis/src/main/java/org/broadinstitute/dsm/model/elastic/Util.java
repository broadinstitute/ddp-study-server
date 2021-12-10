package org.broadinstitute.dsm.model.elastic;

import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.PatchUtil;

public class Util {

    public static final Map<String, BaseGenerator.PropertyInfo> TABLE_ALIAS_MAPPINGS = Map.of(
            "m", new BaseGenerator.PropertyInfo(ESObjectConstants.MEDICAL_RECORDS, true),
            "t", new BaseGenerator.PropertyInfo(ESObjectConstants.TISSUE_RECORDS, true),
            "oD", new BaseGenerator.PropertyInfo(ESObjectConstants.ONC_HISTORY_DETAIL_RECORDS, true),
            "d", new BaseGenerator.PropertyInfo(ESObjectConstants.PARTICIPANT_DATA, true),
            "r", new BaseGenerator.PropertyInfo(ESObjectConstants.PARTICIPANT_RECORD, false),
            "p", new BaseGenerator.PropertyInfo(ESObjectConstants.PARTICIPANT, false),
            "o", new BaseGenerator.PropertyInfo(ESObjectConstants.ONC_HISTORY, false)
            );

    public static String getQueryTypeFromId(String id) {
        String type;
        if (ParticipantUtil.isHruid(id)) {
            type = Constants.PROFILE_HRUID;
        } else if (ParticipantUtil.isGuid(id)){
            type = Constants.PROFILE_GUID;
        } else if (ParticipantUtil.isLegacyAltPid(id)) {
            type = Constants.PROFILE_LEGACYALTPID;
        } else {
            type = Constants.PROFILE_LEGACYSHORTID;
        }
        return type;
    }

    public static DBElement getDBElement(String fieldName) {
        return PatchUtil.getColumnNameMap().get(Objects.requireNonNull(fieldName));
    }

    public static class Constants {
        public static final String PROFILE = "profile";
        public static final String PROFILE_HRUID = PROFILE + ".hruid";
        public static final String PROFILE_GUID = PROFILE + ".guid";
        public static final String PROFILE_LEGACYALTPID = PROFILE + ".legacyAltPid";
        public static final String PROFILE_LEGACYSHORTID = PROFILE + ".legacyShortId";
    }
}
