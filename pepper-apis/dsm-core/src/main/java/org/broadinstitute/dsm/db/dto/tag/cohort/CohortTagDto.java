package org.broadinstitute.dsm.db.dto.tag.cohort;

import lombok.Data;

@Data
public class CohortTagDto {

    Integer id;
    String tagName;
    String ddpParticipantId;
    Integer ddpInstanceId;
}
