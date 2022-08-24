package org.broadinstitute.dsm.db.dao.tag.cohort;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CohortTagDaoTest {

    private List<Integer> createdTagsIds;
    private CohortTagDao cohortTagDao;

    @BeforeClass
    public static void mainSetUp() {
        TestHelper.setupDB();
    }

    @Before
    public void setUp() {
        createdTagsIds = new ArrayList<>();
        cohortTagDao = new CohortTagDaoImpl();
    }

    @After
    public void finish() {
        for (Integer id: createdTagsIds) {
            cohortTagDao.delete(id);
        }
    }

    @Test
    public void bulkCohortCreate() {
        List<CohortTag> cohortTags = Arrays.asList(
                new CohortTag(0, "testTag", "TEST01", 8),
                new CohortTag(0, "testTag2", "TEST02", 8)
        );
        List<Integer> ids = cohortTagDao.bulkCohortCreate(cohortTags);
        createdTagsIds.addAll(ids);
        assertEquals(2, ids.size());
        ids.forEach(id -> {
            assertTrue(id > 0);
        });
    }
}
