package org.broadinstitute.dsm.model.patch;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.util.NotificationUtil;

public class PatchFactory {

    public static BasePatch makePatch(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher = new NullPatch();
        if (isExistingRecord(patch)) {
            patcher = ExistingRecordPatchFactory.produce(patch, notificationUtil);
        } else if (isParentWithExistingKey(patch)) {
            if (isParentParticipantId(patch)) {
                if (isMedicalRecordAbstractionFieldId(patch)) {
                    patcher = new AbstractionPatch(patch);
                } else {
                    patcher = new OncHistoryDetailPatch(patch);
                }
            } else if (isTissueRelatedOncHistoryId(patch)) {
                patcher = new TissuePatch(patch);
            } else if (isSmIdCreation(patch)) {
                patcher = new SMIDPatch(patch);
            } else if (isParentParticipandDataId(patch)) {
                patcher = new ParticipantDataPatch(patch);
            } else if (isParticipantIdForRecord(patch)) {
                patcher = new ParticipantRecordPatch(patch);
            }
        }
        if (patcher instanceof NullPatch) {
            throw new RuntimeException("Id and parentId was null");
        }
        patcher.setElasticSearchExportable(isElasticSearchExportable(patch));
        return patcher;
    }

    private static boolean isSmIdCreation(Patch patch) {
        return TissuePatch.TISSUE_ID.equals(patch.getParent());
    }

    private static boolean isElasticSearchExportable(Patch patch) {
        if (Objects.isNull(patch.getTableAlias())) {
            return false;
        }
        return PropertyInfo.TABLE_ALIAS_MAPPINGS.containsKey(patch.getTableAlias());
    }

    private static boolean isExistingRecord(Patch patch) {
        return StringUtils.isNotBlank(patch.getId());
    }

    private static boolean isParentWithExistingKey(Patch patch) {
        return StringUtils.isNotBlank(patch.getParent()) && StringUtils.isNotBlank(patch.getParentId());
    }

    private static boolean isParentParticipantId(Patch patch) {
        return Patch.PARTICIPANT_ID.equals(patch.getParent());
    }

    private static boolean isMedicalRecordAbstractionFieldId(Patch patch) {
        return StringUtils.isNotBlank(patch.getFieldId());
    }

    private static boolean isTissueRelatedOncHistoryId(Patch patch) {
        return OncHistoryDetail.ONC_HISTORY_DETAIL_ID.equals(patch.getParent());
    }

    private static boolean isParentParticipandDataId(Patch patch) {
        return Patch.PARTICIPANT_DATA_ID.equals(patch.getParent());
    }

    private static boolean isParticipantIdForRecord(Patch patch) {
        return Patch.DDP_PARTICIPANT_ID.equals(patch.getParent());
    }
}
