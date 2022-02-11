package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.MedicalRecordMigrationTool;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MedicalRecordMigrationToolTest extends TestHelper {

    @BeforeClass
    public static void first() {
        setupDB();
    }

    @Test
    public void testMigrationTool() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        MedicalRecordMigrationTool.argumentsForTesting("config/test-config.conf", TEST_DDP, "MedicalRecordMigrationMBC.txt");
        MedicalRecordMigrationTool.littleMain();

        //check value of participant 1
        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecordMigrationTool.SQL_SELECT_MEDICAL_RECORD_INFORMATION + " and inst.ddp_institution_id = \"40324\"", "20160", "medical_record_id");
        String valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "fax_sent");
        Assert.assertEquals("2016-02-28", valueFromDB);
        valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "mr_received");
        Assert.assertEquals("2017-01-17", valueFromDB);

        //check value of participant 2 and second institution
        medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecordMigrationTool.SQL_SELECT_MEDICAL_RECORD_INFORMATION + " and inst.ddp_institution_id = \"40347\"", "20164", "medical_record_id");
        valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "fax_sent");
        Assert.assertEquals("2016-03-01", valueFromDB);
        valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "mr_received");
        Assert.assertEquals("2017-01-19", valueFromDB);

        //check value of last participant
        medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecordMigrationTool.SQL_SELECT_MEDICAL_RECORD_INFORMATION + " and inst.ddp_institution_id = \"40390\"", "20170", "medical_record_id");
        valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "fax_sent");
        Assert.assertEquals("2016-03-02", valueFromDB);
        valueFromDB = DBTestUtil.getQueryDetail(RouteTest.SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, "mr_received");
        Assert.assertEquals("2017-01-20", valueFromDB);
    }

    @Test
    public void longFromShortDateString() {
        String dateString = "2/28/16";
        Long dateLong = 1456635600 * 1000L;
        Assert.assertEquals(dateLong, DBUtil.getLong(dateString));
    }

    @Test
    public void changedDateString() {
        String dateString = "2/28/16";
        String changedDate = "2016-02-28";
        Assert.assertEquals(changedDate, DBUtil.changeDateFormat(dateString));
    }

    @AfterClass
    public static void stopMockServer() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        TransactionWrapper.init(cfg.getInt("portal.maxConnections"), cfg.getString("portal.dbUrl"), cfg, false);
        //delete all KitRequests added by the test
        DBTestUtil.deleteAllParticipantData("66666");
        DBTestUtil.deleteAllParticipantData("20160", true);
        DBTestUtil.deleteAllParticipantData("20164", true);
        DBTestUtil.deleteAllParticipantData("20170", true);
    }
}
