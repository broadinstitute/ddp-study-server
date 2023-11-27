package org.broadinstitute.dsm.db.dao.tag.cohort;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

public interface CohortTagDao extends Dao<CohortTag> {
    Map<String, List<CohortTag>> getCohortTagsByInstanceName(String instanceName);

    List<Integer> bulkCohortCreate(List<CohortTag> cohortTags);

    int removeCohortByCohortTagNameAndGuid(String cohortTagName, String ddpParticipantId);

    boolean participantHasTag(String ddpParticipantId, String tagName);
}
