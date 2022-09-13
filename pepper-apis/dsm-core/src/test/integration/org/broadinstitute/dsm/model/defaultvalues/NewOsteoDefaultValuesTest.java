package org.broadinstitute.dsm.model.defaultvalues;

import static org.junit.Assert.*;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.junit.BeforeClass;
import org.junit.Test;

public class NewOsteoDefaultValuesTest {

    private static CohortTagDao cohortTagDao;
    private static ElasticSearchable elasticSearchable;

    private static final String TEST_GUID = "TEST_GUID_0000000001";

    private static final String STUDY_GUID = "cmi-osteo";


    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        cohortTagDao = new CohortTagDaoImpl();
        elasticSearchable = new ElasticSearch();
    }


    @Test
    public void generateDefaults() {
        NewOsteoDefaultValues newOsteoDefaultValues = new NewOsteoDefaultValues();
        boolean isGenerated = newOsteoDefaultValues.generateDefaults(STUDY_GUID, TEST_GUID);
        assertTrue(isGenerated);
    }

}