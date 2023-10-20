package org.broadinstitute.dsm.model.patch;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.NotificationUtil;

public class PatchFactory {

    private PatchFactory(){
        throw new IllegalStateException("Utility class");
    }

    public static BasePatch makePatch(Patch patch, NotificationUtil notificationUtil) {
        BasePatch patcher = new NullPatch();
        if (isDeletePatch(patch)) {
            patcher = DeletePatchFactory.produce(patch, notificationUtil);
        } else if (isExistingRecord(patch)) {
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
        } else if (isOncHistoryDetailPatch(patch)) {
            patcher = new OncHistoryDetailPatch(patch);
        }
        if (patcher instanceof NullPatch) {
            throw new DSMBadRequestException("Id and parentId was null in the patch request");
        }
        patcher.setElasticSearchExportable(isElasticSearchExportable(patch));
        return patcher;
    }

    private static boolean isOncHistoryDetailPatch(Patch patch) {
        return isParentParticipantId(patch) && !isMedicalRecordAbstractionFieldId(patch)
                && StringUtils.isNotBlank(patch.getDdpParticipantId());
    }

    public static boolean isDeletePatch(Patch patch) {
        // check that the patch is for updating a `deleted` flags, and it's for either onchistory or tissue filters
        return patch.getNameValue().getName().contains(".deleted") &&
                (DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(patch.getTableAlias()) || PatchFactory.isTissueRelatedOncHistoryId(patch));
    }

    private static boolean isSmIdCreation(Patch patch) {
        return TissuePatch.TISSUE_ID.equals(patch.getParent());
    }

    private static boolean isElasticSearchExportable(Patch patch) {
        if (Objects.isNull(patch.getTableAlias())) {
            return false;
        }
        return PropertyInfo.hasProperty(patch.getTableAlias());
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

    public static boolean isTissueRelatedOncHistoryId(Patch patch) {
        return OncHistoryDetail.ONC_HISTORY_DETAIL_ID.equals(patch.getParent());
    }

    private static boolean isParentParticipandDataId(Patch patch) {
        return Patch.PARTICIPANT_DATA_ID.equals(patch.getParent());
    }

    private static boolean isParticipantIdForRecord(Patch patch) {
        return Patch.DDP_PARTICIPANT_ID.equals(patch.getParent());
    }
}
