
package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Map;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.Util;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.pubsub.study.osteo.OsteoWorkflowStatusUpdate;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class NewOsteoDefaultValuesTest {

    public static final String PARTICIPANTS_STRUCTURED_CMI_CMI_OSTEO = "participants_structured.cmi.cmi-osteo";
    private static CohortTagDao cohortTagDao;
    private static ElasticSearchable elasticSearchable;

    private static final String TEST_GUID = "TEST_GUID_0000000001";
    private static final String STUDY_GUID = "cmi-osteo";

    @AfterClass
    public static void finish() {
        cohortTagDao.removeCohortByCohortTagNameAndGuid(OsteoWorkflowStatusUpdate.NEW_OSTEO_COHORT_TAG_NAME, TEST_GUID);
        elasticSearchable.deleteDocumentById(PARTICIPANTS_STRUCTURED_CMI_CMI_OSTEO, TEST_GUID);
    }

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        cohortTagDao = new CohortTagDaoImpl();
        elasticSearchable = new ElasticSearch();
        elasticSearchable.createDocumentById(PARTICIPANTS_STRUCTURED_CMI_CMI_OSTEO, TEST_GUID, Map.of(
                ESObjectConstants.DSM, Map.of(),
                ESObjectConstants.PROFILE, Map.of(ESObjectConstants.GUID, TEST_GUID)
        ));
        try {
            Util.waitForCreationInElasticSearch();
        } catch (InterruptedException e) {
            Assert.fail();
        }
    }


    @Test
    @Ignore
    public void generateDefaults() {
        NewOsteoDefaultValues newOsteoDefaultValues = new NewOsteoDefaultValues();
        boolean isGenerated = newOsteoDefaultValues.generateDefaults(STUDY_GUID, TEST_GUID);
        try {
            Util.waitForCreationInElasticSearch();
        } catch (InterruptedException e) {
            Assert.fail();
        }
        Assert.assertTrue(isGenerated);
        ElasticSearchParticipantDto participant =
                elasticSearchable.getParticipantById(PARTICIPANTS_STRUCTURED_CMI_CMI_OSTEO, TEST_GUID);
        participant.getDsm().ifPresentOrElse(dsm -> {
            Assert.assertEquals(1, dsm.getCohortTag().size());
            CohortTag cohortTag = dsm.getCohortTag().get(0);
            Assert.assertEquals(OsteoWorkflowStatusUpdate.NEW_OSTEO_COHORT_TAG_NAME, cohortTag.getCohortTagName());
        }, Assert::fail);
    }

}
