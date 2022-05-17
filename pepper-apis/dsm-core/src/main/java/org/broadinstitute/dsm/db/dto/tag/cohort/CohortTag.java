package org.broadinstitute.dsm.db.dto.tag.cohort;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.COHORT_TAG,
        alias = DBConstants.COHORT_ALIAS,
        primaryKey = DBConstants.COHORT_TAG_PK,
        columnPrefix = "")
public class CohortTag {

    @ColumnName(DBConstants.COHORT_TAG_PK)
    Integer cohortTagId;
    @ColumnName(DBConstants.COHORT_TAG_NAME)
    String cohortTagName;
    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    String ddpParticipantId;
    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    Integer ddpInstanceId;
}
