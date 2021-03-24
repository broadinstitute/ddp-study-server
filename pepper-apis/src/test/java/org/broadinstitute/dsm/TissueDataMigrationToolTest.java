package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.TissueDataMigrationTool;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


public class TissueDataMigrationToolTest extends TestHelper {
    public final String query = "SELECT * FROM ddp_onc_history_detail oncDetail " +
            "LEFT JOIN ddp_tissue tissue ON (oncDetail.onc_history_detail_id = tissue.onc_history_detail_id) " +
            "WHERE oncDetail.accession_number = ?";

    @BeforeClass
    public static void first() {
        setupDB();
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_2", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_3", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_4", true);
    }

    @Test
    public void testMigrationTool() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);

        TissueDataMigrationTool tool = new TissueDataMigrationTool("AngioTissueMigrationTestFile.txt", "GEC");
        String phone = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "phone");
        Assert.assertEquals("111-222-3333", phone);//  phone was the same everytime this accession number was in there
        String accessionNumber = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "accession_number");
        Assert.assertEquals("TEST_ACC_NUM1", accessionNumber);
        String datePx = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "date_px");
        Assert.assertEquals("2018-08-21", datePx);//  datePx was the same everytime this accession number was in there
        String typePx = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "type_px");
        Assert.assertEquals("TYPE1", typePx);
        String locationPx = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "location_px");
        Assert.assertEquals("LOC1", locationPx);
        String histology = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "histology");
        Assert.assertEquals("HIST1", histology);
        String facility = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "facility");
        Assert.assertEquals("FACILITY1", facility);
        String fax = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "fax");
        Assert.assertEquals("111-222-3333", fax);
        String request = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "request");
        Assert.assertEquals("received", request);
        String faxSent = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "fax_sent");
        Assert.assertEquals("2018-03-09", faxSent);
        String tissueReceived = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tissue_received");
        Assert.assertEquals("2018-10-09", tissueReceived);// a date field
        String tissueNotes = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tissue.notes");
        Assert.assertNull(tissueNotes);
        String notes = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "oncDetail.notes");
        Assert.assertEquals("A NEW FAKE TEST PARTICIPANT", notes);// the notes
        String destructionPolicy = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "destruction_policy");
        Assert.assertEquals("1", destructionPolicy);
        String gender = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "gender");
        Assert.assertEquals("male", gender);
        String countReceived = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "count_received");
        Assert.assertEquals("3", countReceived);
        String tissueType = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tissue_type");
        Assert.assertEquals("block", tissueType);
        String hE = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "h_e");
        Assert.assertEquals("no", hE);
        String pathReport = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "pathology_report");
        Assert.assertEquals("yes", pathReport);
        String tSite = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tissue_site");
        Assert.assertEquals("SITE1", tSite);
        String collabId = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "collaborator_sample_id");
        Assert.assertEquals("TEST_COLLAB1", collabId);
        String blockSent = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "block_sent");
        Assert.assertEquals("2018-04-10", blockSent);
        String scrollsReceived = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "scrolls_received");
        Assert.assertEquals("2018-10-10", scrollsReceived);
        String skId = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "sk_id");
        Assert.assertEquals("FAKE_SK1", skId);// a field that should have not been overwritten
        String firstSmId = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "first_sm_id");
        Assert.assertEquals("FAKE_FSM1", firstSmId);
        String smId = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "sm_id");
        Assert.assertEquals("FAKE_SM1", smId);
        String tumorType = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tumor_type");
        Assert.assertNotNull(tumorType);
        Assert.assertEquals("primary", tumorType);// a field that should have been overwritten
        String sentGp = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "sent_gp");
        Assert.assertNull(sentGp);
        String expecReturn = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "expected_return");
        Assert.assertEquals("N/A", expecReturn);
        String additionalValues = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "additional_tissue_value_json");
        Assert.assertEquals("{\"consult1\":\"FAKE_CONS1\"}", additionalValues);// the additional values
        String returnDate = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "return_date");
        Assert.assertEquals("2018-06-11", returnDate);
        String returnFedex = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "return_fedex_id");
        Assert.assertEquals("123123123", returnFedex);
        String problem1 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM1", "tissue_problem_option");
        Assert.assertEquals("noESign", problem1);

        String phone4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4", "phone");// This row of data should have been ignored
        Assert.assertNull(phone4);
        String tissueReceived4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4", "tissue_received");
        Assert.assertNull(tissueReceived4);
        String additionalValues4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4", "additional_tissue_value_json");
        Assert.assertNull(additionalValues4);
        String smId4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4", "sm_id");
        Assert.assertNull(smId4);
        String destructionPolicy4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4", "destruction_policy");
        Assert.assertNull(destructionPolicy4);


        String type4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "type_px");// This row of data should have been ignored
        Assert.assertEquals(type4, "TYPE6");
        String request4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "request");
        Assert.assertEquals(request4, "sent");
        String problem4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "tissue_problem_option");
        Assert.assertEquals(problem4, "insufficientSHL");
        String smId4b = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "sm_id");
        Assert.assertNull(smId4b);
        String destructionPolicy4b = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "destruction_policy");
        Assert.assertNull(destructionPolicy4b);
        String tumorType4 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM4b", "tumor_type");
        Assert.assertEquals(tumorType4, "met");

        String phone2 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2", "phone");
        Assert.assertNull(phone2);
        String tissueReceived2 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2", "tissue_received");
        Assert.assertNull(tissueReceived2);// a date field
        String additionalValues2 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2", "additional_tissue_value_json");
        Assert.assertNull(additionalValues2);// the additional values
        String smId2 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2", "sm_id");
        Assert.assertNull(smId2);// a field that was null in second data
        String destructionPolicy2 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2", "destruction_policy");
        Assert.assertNull(destructionPolicy2);// a date field

        String tissueType2a = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2a", "tissue_type");
        Assert.assertEquals(tissueType2a, "slide");// a date field
        String tumorType2a = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM2a", "tumor_type");
        Assert.assertEquals("recurrent", tumorType2a);// a date field

        String phone3 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM3", "phone");
        Assert.assertEquals("111-222-3335", phone3);//  phone was the same everytime this accession number was in there
        String destructionPolicy3 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM3", "destruction_policy");
        Assert.assertEquals("3", destructionPolicy3);// a date field
        String smId3 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM3", "sm_id");
        Assert.assertNull(smId3);
        String additionalValues3 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM3", "additional_tissue_value_json");
        Assert.assertNull(additionalValues3);// the additional values
        String problem3 = DBTestUtil.getQueryDetail(query, "TEST_ACC_NUM3", "tissue_problem_option");
        Assert.assertEquals(problem3, "other");

    }

    @AfterClass
    public static void stopMockServer() {
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_2", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_3", true);
        DBTestUtil.deleteAllParticipantData("FAKE_ANGIO_MIGRATION_4", true);
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        TransactionWrapper.init(cfg.getInt("portal.maxConnections"), cfg.getString("portal.dbUrl"), cfg, false);
        //delete all KitRequests added by the test
    }


}

