package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

public class CohortTagTestUtil {

    private static final CohortTagDao cohortTagDao = new CohortTagDaoImpl();

    public static CohortTag createTag(String cohortTagName, String ddpParticipantId, int ddpInstanceId) {
        CohortTag cohortTag = new CohortTag(cohortTagName, ddpParticipantId, ddpInstanceId);
        int id = cohortTagDao.create(cohortTag);
        cohortTag.setCohortTagId(id);
        return cohortTag;
    }

    public static void deleteTag(String ddpParticipantId, String cohortTagName) {
        cohortTagDao.removeCohortByCohortTagNameAndGuid(cohortTagName, ddpParticipantId);
    }
}
