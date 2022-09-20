package org.broadinstitute.dsm.db.dto.tag.cohort;

import java.util.Objects;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.SystemUtil;

@Data
@TableName(
        name = DBConstants.COHORT_TAG,
        alias = DBConstants.COHORT_ALIAS,
        primaryKey = DBConstants.COHORT_TAG_PK,
        columnPrefix = "")
public class CohortTag implements Cloneable {

    @ColumnName(DBConstants.COHORT_TAG_PK)
    Integer cohortTagId;
    @ColumnName(DBConstants.COHORT_TAG_NAME)
    String cohortTagName;
    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    String ddpParticipantId;
    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    Integer ddpInstanceId;

    String createdBy;

    public CohortTag(Integer cohortTagId, String cohortTagName, String ddpParticipantId, Integer ddpInstanceId) {
        this.cohortTagId = cohortTagId;
        this.cohortTagName = cohortTagName;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
    }

    public CohortTag(String cohortTagName, String ddpParticipantId, Integer ddpInstanceId) {
        this.cohortTagName = cohortTagName;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.createdBy = SystemUtil.SYSTEM;
    }

    public CohortTag() {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CohortTag cohortTag = (CohortTag) o;
        return cohortTagId.equals(cohortTag.cohortTagId) && cohortTagName.equals(cohortTag.cohortTagName)
                && ddpParticipantId.equals(cohortTag.ddpParticipantId) && ddpInstanceId.equals(cohortTag.ddpInstanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cohortTagId, cohortTagName, ddpParticipantId, ddpInstanceId);
    }

    @Override
    public CohortTag clone() {
        try {
            CohortTag clone = (CohortTag) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
