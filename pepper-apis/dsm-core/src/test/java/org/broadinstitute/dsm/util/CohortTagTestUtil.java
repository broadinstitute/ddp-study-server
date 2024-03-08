package org.broadinstitute.dsm.util;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.junit.Assert;

public class CohortTagTestUtil {
    private static final CohortTagDao cohortTagDao = new CohortTagDaoImpl();
    private final List<Integer> cohortIds = new ArrayList<>();

    public CohortTagTestUtil() {
    }

    public void tearDown() {
        try {
            cohortIds.forEach(cohortTagDao::delete);
            cohortIds.clear();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Error in tearDown " + e.toString());
        }
    }

    public CohortTag createTag(String cohortTagName, String ddpParticipantId, int ddpInstanceId) {
        CohortTag cohortTag = new CohortTag(cohortTagName, ddpParticipantId, ddpInstanceId);
        int id = cohortTagDao.create(cohortTag);
        cohortTag.setCohortTagId(id);
        cohortIds.add(id);
        return cohortTag;
    }
}
