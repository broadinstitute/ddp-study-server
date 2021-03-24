package org.broadinstitute.dsm;

import com.google.gson.JsonObject;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.model.NDIUploadObject;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.gbf.LineItem;
import org.broadinstitute.dsm.model.gbf.Orders;
import org.broadinstitute.dsm.model.gbf.ShippingConfirmations;
import org.broadinstitute.dsm.model.gbf.ShippingInfo;
import org.broadinstitute.dsm.route.NDIRoute;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.junit.*;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DirectMethodTest extends TestHelper {

    @BeforeClass
    public static void first() {
        setupDB();
        DBTestUtil.deleteAllParticipantData("66666666");
        DBTestUtil.createTestData(TEST_DDP, "TEST_PARTICIPANT", "TEST_INSTITUTION");
    }

    @AfterClass
    public static void stopServer() {
        DBTestUtil.deleteAllParticipantData("66666666");
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
        cleanupDB();
    }

    @Before
    public void beforeTest() {
        DBTestUtil.deleteAllFieldSettings(TEST_DDP);
    }

    @Test
    public void getFieldSettingsTest() {
        //This test assumes that the before method has removed any TEST_DDP settings from the field_settings table
        //and that queries of the database return what they should.

        //Add some settings to the database table
        List<Value> possibleTissueValues = new ArrayList<>();
        possibleTissueValues.add(new Value("TestTissueOption1"));
        possibleTissueValues.add(new Value("TestTissueOption2"));
        possibleTissueValues.add(new Value("TestTissueOption3"));
        DBTestUtil.createAdditionalFieldForRealm("testOncName", "testOncDisplay", "oD", "text", null);
        DBTestUtil.createAdditionalFieldForRealm("testTissueName", "testTissueDisplay", "t", "select", possibleTissueValues);
        ArrayList<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);

        //As a safeguard, make sure that the settings we just added are in fact in the table
        String stringFromQuery = DBTestUtil.getStringFromQuery("select count(*) from field_settings where ddp_instance_id = (" +
                        "select ddp_instance_id from ddp_instance where instance_name = ?) and not (deleted <=> 1)",
                strings, "count(*)");
        Assert.assertEquals("getFieldSettingsTest: Configuration error: More than two settings found for TEST_DDP", 2, Integer.parseInt(stringFromQuery));

        //Test the getFieldSettings method
        Map<String, Collection<FieldSettings>> fieldSettings = FieldSettings.getFieldSettings(TEST_DDP);

        //Make sure the settings are sorted appropriately
        Assert.assertEquals("getFieldSettingsTest: There should be exactly two types of settings", 2, fieldSettings.size());
        Assert.assertTrue("getFieldSettingsTest: onc history list not found", fieldSettings.containsKey("oD"));
        Assert.assertTrue("getFieldSettingsTest: tissue list not found", fieldSettings.containsKey("t"));

        //Make sure there is one of each kind of setting
        Collection<FieldSettings> oncHistorySettings = fieldSettings.get("oD");
        Collection<FieldSettings> tissueSettings = fieldSettings.get("t");

        Assert.assertEquals("getFieldSettingsTest: wrong number of oncHistory settings found", 1, oncHistorySettings.size());
        Assert.assertEquals("getFieldSettingsTest: wrong number of tissue settings found", 1, tissueSettings.size());

        //Make sure the settings match the ones we created
        for (FieldSettings oncSetting : oncHistorySettings) {
            DBTestUtil.checkSettingMatch(oncSetting, "oD",
                    "testOncDisplay", "testOncName", "text",
                    null, false, "setting named testOncName");
        }

        for (FieldSettings tissueSetting : tissueSettings) {
            DBTestUtil.checkSettingMatch(tissueSetting, "t",
                    "testTissueDisplay", "testTissueName",
                    "select", possibleTissueValues, false,
                    "setting named testTissueName");
        }
    }

    private Map<String, Collection<FieldSettings>> constructFieldSettingsMap(String settingId,
                                                                             @NonNull String columnName,
                                                                             @NonNull String columnDisplay,
                                                                             @NonNull String fieldType,
                                                                             @NonNull String displayType,
                                                                             List<Value> possibleValues,
                                                                             int orderNumber,
                                                                             boolean deleted) {
        FieldSettings setting = new FieldSettings(settingId, columnName, columnDisplay, fieldType, displayType,
                possibleValues, orderNumber, null);
        if (settingId != null && deleted) {
            setting.setDeleted(true);
        }
        return constructFieldSettingsMap(fieldType, setting);
    }

    private Map<String, Collection<FieldSettings>> constructFieldSettingsMap(@NonNull String fieldType,
                                                                             @NonNull FieldSettings setting) {
        Collection<FieldSettings> settingsCollection = new ArrayList<>();
        settingsCollection.add(setting);
        Map<String, Collection<FieldSettings>> settingsMap = new HashMap<>();
        settingsMap.put(fieldType, settingsCollection);
        return settingsMap;
    }

    @Test
    public void saveFieldSettingsTest() {
        //This test assumes that the before method has removed any TEST_DDP settings from the field_settings table
        //and that queries of the database return what they should.

        //Create some example settings
        Map<String, Collection<FieldSettings>> oncSettingsLists = constructFieldSettingsMap(null,
                "customOncName", "customOncDisplay", "oD",
                "textarea", null, 1, false);
        List<Value> options = new ArrayList<>();
        options.add(new Value("tissueo1"));
        options.add(new Value("tissueo2"));
        Map<String, Collection<FieldSettings>> tissueSettingsLists = constructFieldSettingsMap(null,
                "customTissueName", "customTissueDisplay", "t",
                "multiselect", options, 1, false);

        //Use setFieldSettings to add the onc history setting
        FieldSettings.saveFieldSettings(TEST_DDP, oncSettingsLists, "TEST_USER");

        //Use setFieldSettings to add the tissue setting
        FieldSettings.saveFieldSettings(TEST_DDP, tissueSettingsLists, "TEST_USER");

        //Check the field_settings table in the database to make sure the new settings were added
        //Make sure there are now two TEST_DDP settings
        List<String> strings = new ArrayList<>();
        strings.add(TEST_DDP);
        String stringFromQuery = DBTestUtil.getStringFromQuery("select count(*) from field_settings " +
                "where ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) " +
                "and not (deleted <=> 1)", strings, "count(*)");

        Assert.assertEquals("saveFieldSettingsTest: wrong number of field settings returned", 2,
                Integer.parseInt(stringFromQuery));

        //Make sure there is one oncHistory setting and get its field_settings_id
        String idQuery = "select * from field_settings where ddp_instance_id = (select ddp_instance_id from " +
                "ddp_instance where instance_name = ?) and not (deleted <=> 1) and field_type = ?";
        strings.add("oD");
        String oncId = DBTestUtil.getStringFromQuery(idQuery, strings, "field_settings_id");

        //Make sure the oncHistory setting matches what we expect
        DBTestUtil.checkSettingMatch(oncId, "oD", "customOncName",
                "customOncDisplay", "textarea", null,
                false);

        //Make sure there is one tissue setting and get its field_settings_id
        strings.remove("oD");
        strings.add("t");
        String tissueId = DBTestUtil.getStringFromQuery(idQuery, strings, "field_settings_id");

        //Make sure the tissue setting matches what we expect
        DBTestUtil.checkSettingMatch(tissueId, "t", "customTissueName",
                "customTissueDisplay", "multiselect", options, false);

        //Update the onc history setting by changing the display name
        oncSettingsLists = constructFieldSettingsMap(oncId, "customOncName", "new onc display",
                "oD", "textarea", null, 2, false);
        FieldSettings.saveFieldSettings(TEST_DDP, oncSettingsLists, "TEST_USER");

        //Check the table to make sure it got updated correctly
        DBTestUtil.checkSettingMatch(oncId, "oD",
                "customOncName", "new onc display", "textarea",
                null, false);

        //"Update" the tissue setting by setting deleted to true
        tissueSettingsLists = constructFieldSettingsMap(tissueId, "customTissueName", "customTissueDisplay",
                "t", "multiselect", options, 2, true);
        FieldSettings.saveFieldSettings(TEST_DDP, tissueSettingsLists, "TEST_USER");

        //Make sure it was effectively deleted
        DBTestUtil.checkSettingMatch(tissueId, "t", "customTissueName",
                "customTissueDisplay", "multiselect", options, true);
    }

    @Test
    public void tissue() {
        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"TEST_INSTITUTION\" and p.ddp_participant_id = \"TEST_PARTICIPANT\"", TEST_DDP, "medical_record_id");

        //get oncHistoryDetailId
        ArrayList strings = new ArrayList<>();
        strings.add(medicalRecordId);
        String oncHistoryDetailId = DBTestUtil.getStringFromQuery("select * from ddp_onc_history_detail where medical_record_id = ?", strings, "onc_history_detail_id");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = Tissue.getTissue(conn, oncHistoryDetailId);
            return dbVals;
        });

        if (results.resultException != null) {
            Assert.fail(results.resultException.getMessage());
        }

        List<Tissue> tissues = (List<Tissue>) results.resultValue;
        Assert.assertNotNull(tissues);

        strings = new ArrayList<>();
        strings.add(oncHistoryDetailId);
        Assert.assertEquals(DBTestUtil.getStringFromQuery("select count(*) from ddp_tissue where onc_history_detail_id = ?", strings, "count(*)"), String.valueOf(tissues.size()));
    }

    @Test
    public void oncHistoryDetail() {
        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"TEST_INSTITUTION\" and p.ddp_participant_id = \"TEST_PARTICIPANT\"", TEST_DDP, "medical_record_id");

        //get oncHistoryDetailId
        ArrayList strings = new ArrayList<>();
        strings.add(medicalRecordId);
        String oncHistoryDetailId = DBTestUtil.getStringFromQuery("select * from ddp_onc_history_detail where medical_record_id = ?", strings, "onc_history_detail_id");


        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, TEST_DDP);
            return dbVals;
        });

        if (results.resultException != null) {
            Assert.fail(results.resultException.getMessage());
        }

        OncHistoryDetail oncHistoryDetail = (OncHistoryDetail) results.resultValue;
        Assert.assertNotNull(oncHistoryDetail);
    }

    @Test
    public void medicalRecord() {
        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"TEST_INSTITUTION\" and p.ddp_participant_id = \"TEST_PARTICIPANT\"", TEST_DDP, "medical_record_id");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = MedicalRecord.getMedicalRecord(TEST_DDP, "TEST_PARTICIPANT", medicalRecordId);
            return dbVals;
        });

        if (results.resultException != null) {
            Assert.fail(results.resultException.getMessage());
        }

        MedicalRecord medicalRecord = (MedicalRecord) results.resultValue;
        Assert.assertNotNull(medicalRecord);
    }

    @Test
    public void medicalRecords() {
        String participantId = DBTestUtil.getParticipantIdOfTestParticipant("TEST_PARTICIPANT");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            //just get mr without mocked up ddp info
            dbVals.resultValue = MedicalRecord.getMedicalRecords(TEST_DDP);
            return dbVals;
        });

        if (results.resultException != null) {
            Assert.fail(results.resultException.getMessage());
        }

        Map<String, List<MedicalRecord>> medicalRecord = (HashMap<String, List<MedicalRecord>>) results.resultValue;
        Assert.assertNotNull(medicalRecord);

        List<MedicalRecord> medicalRecords = medicalRecord.get("TEST_PARTICIPANT");
        List strings = new ArrayList<>();
        strings.add(participantId);
        Assert.assertEquals(DBTestUtil.getStringFromQuery("select count(*) from ddp_medical_record mr, ddp_institution inst, ddp_participant pat where mr.institution_id = inst.institution_id and inst.participant_id = pat.participant_id and pat.participant_id = ? ", strings, "count(*)"), String.valueOf(medicalRecords.size()));
    }

    @Test
    public void ddpInstance() {
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(TEST_DDP);
        Assert.assertNotNull(ddpInstance);
    }

    @Test
    public void ddpInstanceWithRole() {
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(TEST_DDP, "medical_record_reminder_notifications");
        Assert.assertTrue(ddpInstance.isHasRole()); //that role was added
        ddpInstance = DDPInstance.getDDPInstanceWithRole(TEST_DDP, "needs_name_labels");
        Assert.assertFalse(ddpInstance.isHasRole()); //that role was NOT added
    }

    @Test
    public void ddpsWithRole() {
        List<DDPInstance> ddpInstances = DDPInstance.getDDPInstanceListWithRole("needs_name_labels");
        for (DDPInstance instance : ddpInstances) {
            if (instance.getName().equals(TEST_DDP)) {
                Assert.assertFalse(instance.isHasRole());
                break;
            }
        }
    }

    @Test
    public void exitParticipant() {
        //add test participant
        DBTestUtil.createTestData(TEST_DDP, "EXIT_PARTICIPANT", "TEST_INSTITUTION");
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(TEST_DDP);

        //exit the test participant (call write method to enter participant into exit table)
        ParticipantExit.exitParticipant("EXIT_PARTICIPANT", System.currentTimeMillis(), "1", ddpInstance, false);
        ArrayList strings = new ArrayList<>();
        strings.add(TEST_DDP);
        strings.add("EXIT_PARTICIPANT");

        //check that the participant is in the exit table
        Assert.assertEquals("1", DBTestUtil.getStringFromQuery("select count(*) from ddp_participant_exit where ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) and ddp_participant_id = ?", strings, "count(*)"));

        //get list of exit participants for the test ddp
        Collection<ParticipantExit> exitedParticipants = ParticipantExit.getExitedParticipants(TEST_DDP).values();
        Assert.assertEquals(1, exitedParticipants.size());
    }

    @Test
    public void ndiTestFail() throws Exception {
        String headers = "participantId\tFirst\tMiddle\tLast\tYear\tMonth\tDay";
        String input = "";

        input += headers + "\n";

        String participantId1 = "IAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTVERYLONGPARTICIPANTIDWHICHDOESNOTMAKESENSEBUTWILLTHROWERRORFORCOLLABORATORPARTICIPANTIDANDSAMPLEIDIAMGROOTHOPEFULLYITISNOWLONGENOUGHIAMGROOTIAMGROOT";
        String firstNameShort = randomStringGenerator(10, true, false, false);
        String lastNameShort = randomStringGenerator(15, true, false, false);
        String middleLetter = randomStringGenerator(1, true, false, false);
        String year1 = randomStringGenerator(4, false, false, true);
        String month1 = randomStringGenerator(2, false, false, true);
        String day1 = randomStringGenerator(2, false, false, true);
        String line1 = participantId1 + "\t" + firstNameShort + "\t" + middleLetter + "\t" + lastNameShort + "\t" + year1 + "\t" + month1 + "\t" + day1;
        input += line1 + "\n";


        List<NDIUploadObject> requests = NDIRoute.isFileValid(input);
        Assert.assertNotNull(requests);
        Assert.assertEquals(1, requests.size());
        String output = null;
        try {
            output = NationalDeathIndex.createOutputTxtFile(requests, "test");
        }
        catch (Exception e) {
            Assert.assertEquals("Error inserting control numbers into DB ", e.getMessage());
        }
        Assert.assertNull(output);
    }

    @Test
    public void ndiTest() throws Exception {
        List<String> controlNumber1 = NationalDeathIndex.getAllNdiControlStrings(2);
        Assert.assertEquals(controlNumber1.size(), 2);
        String firstCNumber = controlNumber1.get(0);
        String secondCNumber = controlNumber1.get(1);
        Assert.assertNotEquals(firstCNumber, secondCNumber);

        String uniqueness = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE BINARY ndi_control_number = ?", firstCNumber, "ndi_control_number");
        Assert.assertNull(uniqueness);
        uniqueness = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE BINARY ndi_control_number = ?", secondCNumber, "ndi_control_number");
        Assert.assertNull(uniqueness);

        String afterFirstCNumber = NationalDeathIndex.generateNextControlNumber(firstCNumber);
        Assert.assertEquals(afterFirstCNumber, secondCNumber);

        String headers = "participantId\tFirst\tMiddle\tLast\tYear\tMonth\tDay";
        String input = "";

        input += headers + "\n";

        String participantId1 = "1";
        String firstNameShort = randomStringGenerator(10, true, false, false);
        String lastNameShort = randomStringGenerator(15, true, false, false);
        String middleLetter = randomStringGenerator(3, true, false, false);
        String year1 = randomStringGenerator(4, false, false, true);
        String month1 = randomStringGenerator(2, false, false, true);
        String day1 = randomStringGenerator(2, false, false, true);
        String line1 = participantId1 + "\t" + firstNameShort + "\t" + middleLetter + "\t" + lastNameShort + "\t" + year1 + "\t" + month1 + "\t" + day1;
        input += line1 + "\n";

        String participantId2 = "2";
        String firstNameLong = randomStringGenerator(20, true, false, false);
        String lastNameLong = randomStringGenerator(25, true, false, false);
        String middleEmpty = randomStringGenerator(0, true, false, false);
        String year2 = randomStringGenerator(4, false, false, true);
        String month2 = randomStringGenerator(1, false, false, true);
        String day2 = randomStringGenerator(1, false, false, true);
        String line2 = participantId2 + "\t" + firstNameLong + "\t" + middleEmpty + "\t" + lastNameLong + "\t" + year2 + "\t" + month2 + "\t" + day2;
        input += line2;

        List<NDIUploadObject> requests = NDIRoute.isFileValid(input);
        Assert.assertNotNull(requests);
        Assert.assertEquals(2, requests.size());

        String output = NationalDeathIndex.createOutputTxtFile(requests, "test");
        Assert.assertNotNull(output);
        System.out.println(output);
        Assert.assertEquals(202, output.length());

        String ndiRow1 = output.substring(0, output.indexOf("\n"));
        Assert.assertEquals(100, ndiRow1.length());
        String last1 = ndiRow1.substring(0, 15);
        Assert.assertEquals(lastNameShort, last1);
        Assert.assertEquals("     ", ndiRow1.substring(15, 20));
        String first1 = ndiRow1.substring(20, 30);
        Assert.assertEquals(firstNameShort, first1);
        Assert.assertEquals("     ", ndiRow1.substring(30, 35));
        String middle1 = ndiRow1.substring(35, 36);
        Assert.assertEquals(middleLetter.charAt(0) + "", middle1);
        Assert.assertEquals("         ", ndiRow1.substring(36, 45));
        String dob = ndiRow1.substring(45, 53);
        String mmDob = dob.substring(0, 2);
        Assert.assertEquals(Integer.parseInt(mmDob), Integer.parseInt(month1));
        String ddDob = dob.substring(2, 4);
        Assert.assertEquals(Integer.parseInt(ddDob), Integer.parseInt(day1));
        String yyyyDob = dob.substring(4);
        Assert.assertEquals(Integer.parseInt(yyyyDob), Integer.parseInt(year1));
        String controlNumberNdi = ndiRow1.substring(81, 91);
        Assert.assertEquals(controlNumber1.get(0), controlNumberNdi);
        Assert.assertEquals("  ", ndiRow1.substring(98));
        char[] junk = new char[28];
        Arrays.fill(junk, ' ');
        String junks = String.valueOf(junk);
        Assert.assertEquals(junks, ndiRow1.substring(53, 81));
        Assert.assertEquals("         ", ndiRow1.substring(91));
        String ptIdInDB = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ndi_control_number = ? COLLATE utf8_bin", controlNumber1.get(0), "ddp_participant_id");
        Assert.assertEquals(participantId1, ptIdInDB);

        String ndiRow2 = output.substring(101, output.indexOf("\n", 102));
        Assert.assertEquals(100, ndiRow2.length());
        String last2 = ndiRow2.substring(0, 20);
        Assert.assertEquals(lastNameLong.substring(0, 20), last2);
        String first2 = ndiRow2.substring(20, 35);
        Assert.assertEquals(firstNameLong.substring(0, 15), first2);
        String middle2 = ndiRow2.substring(35, 36);
        Assert.assertEquals(" ", middle2);
        Assert.assertEquals("         ", ndiRow2.substring(36, 45));
        String dob2 = ndiRow2.substring(45, 53);
        String mmDob2 = dob2.substring(0, 2);
        Assert.assertEquals(Integer.parseInt(mmDob2), Integer.parseInt(month2));
        String ddDob2 = dob2.substring(2, 4);
        Assert.assertEquals(Integer.parseInt(ddDob2), Integer.parseInt(day2));
        String yyyyDob2 = dob2.substring(4);
        Assert.assertEquals(Integer.parseInt(yyyyDob2), Integer.parseInt(year2));
        String controlNumberNdi2 = ndiRow2.substring(81, 91);
        Assert.assertEquals(controlNumber1.get(1), controlNumberNdi2);
        Assert.assertEquals("  ", ndiRow2.substring(98));
        Assert.assertEquals(junks, ndiRow2.substring(53, 81));
        Assert.assertEquals("         ", ndiRow2.substring(91));

        String id1 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ndi_control_number = ?", controlNumber1.get(0), "ndi_id");
        String id2 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_ndi WHERE ndi_control_number = ?", controlNumber1.get(1), "ndi_id");
        DBTestUtil.deleteNdiAdded(id1);
        DBTestUtil.deleteNdiAdded(id2);
    }

    @Test
    public void ndiErrorTest() throws Exception {

        String correctHeaders = "participantId\tFirst\tMiddle\tLast\tYear\tMonth\tDay";
        String headers1 = "First\tMiddle\tLast\tYear\tMonth\tDay";
        String input = "";

        input += headers1 + "\n";

        String participantId1 = "1";
        String firstNameShort = randomStringGenerator(10, true, false, false);
        String lastNameShort = randomStringGenerator(15, true, false, false);
        String middleLetter = randomStringGenerator(1, true, false, false);
        String year1 = randomStringGenerator(4, false, false, true);
        String month1 = randomStringGenerator(2, false, false, true);
        String day1 = randomStringGenerator(2, false, false, true);
        String line1 = participantId1 + "\t" + firstNameShort + "\t" + middleLetter + "\t" + lastNameShort + "\t" + year1 + "\t" + month1 + "\t" + day1;
        input += line1 + "\n";
        int size1 = Integer.parseInt(DBTestUtil.selectFromTable("count(*)", "ddp_ndi"));

        List<NDIUploadObject> requests = null;
        try {
            requests = NDIRoute.isFileValid(input);
        }
        catch (FileColumnMissing exception) {
            int size2 = Integer.parseInt(DBTestUtil.selectFromTable("count(*)", "ddp_ndi"));
            Assert.assertEquals(size1, size2);
            Assert.assertEquals(exception.getMessage(), "File is missing column participantId");
        }
        Assert.assertNull(requests);

        input = "";

        input += correctHeaders + "\n";
        String participantId2 = "2";
        String firstName = "";
        String lastName = randomStringGenerator(25, true, false, false);
        String middleEmpty = randomStringGenerator(0, true, false, false);
        String year2 = randomStringGenerator(4, false, false, true);
        String month2 = randomStringGenerator(1, false, false, true);
        String day2 = randomStringGenerator(1, false, false, true);
        String line2 = participantId2 + "\t" + firstName + "\t" + middleEmpty + "\t" + lastName + "\t" + year2 + "\t" + month2 + "\t" + day2;
        input += line2;

        try {
            requests = NDIRoute.isFileValid(input);
        }
        catch (RuntimeException exception) {

            Assert.assertEquals(exception.getMessage(), "Text file is not valid. Couldn't be parsed to upload object ");
        }
        Assert.assertNull(requests);
        int size2 = Integer.parseInt(DBTestUtil.selectFromTable("count(*)", "ddp_ndi"));
        Assert.assertEquals(size1, size2);

        input = "";
        String commaHeaders = "participantId,First,Middle,Last,Year,Month,Day";
        input += commaHeaders + "\n";
        String participantId3 = "3";
        String firstName3 = "name";
        String lastName3 = randomStringGenerator(25, true, false, false);
        String middle3 = randomStringGenerator(0, true, false, false);
        String year3 = randomStringGenerator(4, false, false, true);
        String month3 = randomStringGenerator(1, false, false, true);
        String day3 = randomStringGenerator(1, false, false, true);
        String line3 = participantId3 + "," + firstName3 + "," + middle3 + "," + lastName3 + "," + year3 + "," + month3 + "," + day3;
        input += line3;

        try {
            requests = NDIRoute.isFileValid(input);
        }
        catch (RuntimeException exception) {
            Assert.assertEquals(exception.getMessage(), "Please use tab as separator in the text file");
        }
        Assert.assertNull(requests);
        size2 = Integer.parseInt(DBTestUtil.selectFromTable("count(*)", "ddp_ndi"));
        Assert.assertEquals(size1, size2);

        input = "";
        input += correctHeaders + "\n";
        String line4 = firstName + "\t" + middleEmpty + "\t" + lastName + "\t" + year2 + "\t" + month2 + "\t" + day2;
        input += line4;

        try {
            requests = NDIRoute.isFileValid(input);
        }
        catch (RuntimeException exception) {
            Assert.assertEquals(exception.getMessage(), "Error in line 2");
        }
        Assert.assertNull(requests);
        size2 = Integer.parseInt(DBTestUtil.selectFromTable("count(*)", "ddp_ndi"));
        Assert.assertEquals(size1, size2);
    }

    @Test
    public void drugListEndpoint() {
        List<String> drugList = Drug.getDrugList();
        int drugListSize = drugList.size();

        Set drugListWithoutDupes = new HashSet(drugList);
        int length = drugListWithoutDupes.size();

        Assert.assertFalse(drugList.isEmpty());
        String count = DBTestUtil.getStringFromQuery("select count(*) from drug_list", null, "count(*)");
        Assert.assertEquals(count, String.valueOf(drugListSize));
        Assert.assertTrue("Checking for ABATACEPT (ORENCIA)", drugList.contains("ABATACEPT (ORENCIA)"));
        Assert.assertTrue("Checking for VORINOSTAT", drugList.contains("VORINOSTAT"));
        Assert.assertTrue("Checking for later addition: BRIGATINIB (ALUNBRIG)", drugList.contains("BRIGATINIB (ALUNBRIG)"));
        Assert.assertFalse("Checking for later removal: AFLIBERCEPT (EYLEA)", drugList.contains("AFLIBERCEPT (EYLEA)"));
        Assert.assertEquals("Checking for duplicate entries", drugListSize, length);
    }

    @Test
    public void drugListEntriesGET() {
        List<Drug> drugList = Drug.getDrugListALL();
        int drugListSize = drugList.size();

        Assert.assertFalse(drugList.isEmpty());
        String count = DBTestUtil.getStringFromQuery("select count(*) from drug_list", null, "count(*)");
        Assert.assertEquals(count, String.valueOf(drugListSize));
    }

    @Test // add new drug
    public void drugListingsPATCH() {
        String oldDrugId = DBTestUtil.getStringFromQuery("select drug_id from drug_list where display_name = \'DRUG (TEST)\'", null, "drug_id");
        DBTestUtil.executeQuery("DELETE FROM drug_list WHERE drug_id = " + oldDrugId);

        Drug sampleDrug = new Drug(-1, "DRUG (TEST)", "DRUG", "TEST", "DRUGTEST", "D", false, "H", "N", true);
        Drug.addDrug("1", sampleDrug);

        String drugId = DBTestUtil.getStringFromQuery("select drug_id from drug_list where display_name = \'DRUG (TEST)\'", null, "drug_id");

        // check that the value was changed
        List<String> values = new ArrayList<>();
        values.add(drugId);
        Assert.assertEquals("D", DBTestUtil.getStringFromQuery("select chemo_type from drug_list where drug_id = ?", values, "chemo_type"));

        // Then put the value back to original value
        DBTestUtil.executeQuery("DELETE FROM drug_list WHERE drug_id = " + drugId);
    }

    @Test
    public void cancerListEndpoint() {
        List<String> cancerList = Cancer.getCancers();
        int size = cancerList.size();
        String count = DBTestUtil.getStringFromQuery("SELECT count(*) FROM cancer_list WHERE active=1", null, "count(*)");
        int actualSize = Integer.parseInt(count);
        HashSet<String> noDuplicateCancer = new HashSet<>(cancerList);

        Assert.assertFalse(cancerList.isEmpty());
        Assert.assertEquals("Checking for size to make sure nothing has changed in DB", 65, size);
        Assert.assertEquals("Checking for size", actualSize, size);
        Assert.assertEquals("Checking for duplicate entries", size, noDuplicateCancer.size());
    }

    @Test
    public void notificationRecipient() {
        DDPInstance instance = DDPInstance.getDDPInstance("Prostate");
        List<String> recipients = instance.getNotificationRecipient();
        Assert.assertEquals(2, recipients.size());
        Assert.assertTrue(recipients.contains("simone+1@broadinstitute.org"));
        Assert.assertTrue(recipients.contains("simone+2@broadinstitute.org"));
    }

    @Test
    public void googleBuckets() throws Exception {
        String nameInBucket = "unitTest_fileUpload";
        File file = TestUtil.getResouresFile("BSPscreenshot.png");
        //upload File
        String fileName = GoogleBucket.uploadFile(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"),
                cfg.getString("portal.discardSampleBucket"), nameInBucket, new FileInputStream(file));
        Assert.assertNotNull(fileName);

        //download file
        byte[] downloadedFile = GoogleBucket.downloadFile(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"),
                cfg.getString("portal.discardSampleBucket"), nameInBucket);
        Assert.assertNotNull(downloadedFile);

        //delete file
        boolean fileDeleted = GoogleBucket.deleteFile(cfg.getString("portal.googleProjectCredentials"), cfg.getString("portal.googleProjectName"),
                cfg.getString("portal.discardSampleBucket"), nameInBucket);
        Assert.assertTrue(fileDeleted);
    }

    @Test
    public void gbfOrder() throws Exception {
        org.broadinstitute.dsm.model.gbf.Address address = new org.broadinstitute.dsm.model.gbf.Address("Zulma Medical",
                "19272 Stone Oak Parkway", null, "San Antonio", "TX", "78258", "United States",
                "(210) 265-8851");
        ShippingInfo shippingInfo = new ShippingInfo(null, "Ground, FedEx", address);
        List<LineItem> lineItems = new ArrayList<>();
        lineItems.add(new LineItem("378186", "1"));
        lineItems.add(new LineItem("378188", "2"));
        org.broadinstitute.dsm.model.gbf.Order order = new org.broadinstitute.dsm.model.gbf.Order("ID-000814", "C7037154", "A10000018", shippingInfo, lineItems);
        Orders orders = new Orders();
        orders.setOrders(new ArrayList<>());
        orders.getOrders().add(order);
        String xmlString = GBFRequestUtil.orderXmlToString(Orders.class, orders);
        Assert.assertNotNull(xmlString);
    }

    @Test
    public void gbfConfirmation() throws Exception {
        String shippingConfirmationResponse = TestUtil.readFile("gbf/ShippingConfirmation.xml");
        ShippingConfirmations shippingConfirmations = GBFRequestUtil.objectFromXMLString(ShippingConfirmations.class, shippingConfirmationResponse);
        Assert.assertNotNull(shippingConfirmations);
        Assert.assertTrue(!shippingConfirmations.getShippingConfirmations().isEmpty());
        Assert.assertTrue(shippingConfirmations.getShippingConfirmations().size() == 1);
    }

    @Test
    public void findOrderInShippingConfirmation() throws Exception {
        String shippingConfirmationResponse = TestUtil.readFile("gbf/ShippingConfirmation.xml");
        Node node = GBFRequestUtil.getXMLNode(shippingConfirmationResponse, "/ShippingConfirmations/ShippingConfirmation[@OrderNumber=\'" + "PROM-000824" + "\']");
        Assert.assertNotNull(node);
        String nodeAsString = GBFRequestUtil.getStringFromNode(node);
        Assert.assertTrue(StringUtils.isNotBlank(nodeAsString));
    }

    @Test
    public void externalOrderId() {
        HashMap<String, KitType> kitTypes = KitType.getKitLookup();
        String key = "SUB_KITS_" + PROMISE_INSTANCE_ID;
        KitType kitType = kitTypes.get(key);
        if (kitType == null) {
            throw new RuntimeException("KitType unknown");
        }
        HashMap<Integer, KitRequestSettings> kitRequestSettingsMap = KitRequestSettings.getKitRequestSettings(PROMISE_INSTANCE_ID);
        KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());
        Assert.assertEquals("C7037154", kitRequestSettings.getExternalClientId());
    }

    @Test
    public void json() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "value");
    }

    @Test
    public void instanceSettings() {
        InstanceSettings instanceSettings = InstanceSettings.getInstanceSettings(TEST_DDP);
        Assert.assertNotNull(instanceSettings);
        Assert.assertNotNull(instanceSettings.getMrCoverPdf());
        Assert.assertNotNull(instanceSettings.getKitBehaviorChange());
        Assert.assertFalse(instanceSettings.getMrCoverPdf().isEmpty());
        Assert.assertFalse(instanceSettings.getKitBehaviorChange().isEmpty());

        List<Value> kitBehaviour = instanceSettings.getKitBehaviorChange();
        Value upload = kitBehaviour.stream().filter(o -> o.getName().equals("upload")).findFirst().get();
        if (upload != null && upload.getValues() != null && !upload.getValues().isEmpty()) {
            for (Value condition : upload.getValues()) {
                if (StringUtils.isNotBlank(condition.getName()) && condition.getName().contains(".")) {
                    String[] names = condition.getName().split("\\.");
                }
            }
        }
        else {
            Assert.fail();
        }
    }

    @Ignore ("ES values are changing a lot because of testing")
    @Test
    public void mbcLegacyPTGUID() {
        DDPInstance instance = DDPInstance.getDDPInstance("Pepper-MBC");
        String filter = " AND profile.guid = R0RR2K62F1D4JT2NUF0D";
        Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filter);
        Assert.assertTrue(!participants.isEmpty());
    }

    @Ignore ("ES values are changing a lot because of testing")
    @Test
    public void mbcLegacyPTAltPID() {
        DDPInstance instance = DDPInstance.getDDPInstance("Pepper-MBC");
        String filter = " AND profile.legacyAltPid = 8315_v3";
        Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filter);
        Assert.assertTrue(!participants.isEmpty());
    }

    @Test
    public void osteoSingleCountry() {
        DDPInstance instance = DDPInstance.getDDPInstance("Osteo");
        String filter = " AND ( PREQUAL.SELF_COUNTRY = 'US' )";
        Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filter);
        Assert.assertTrue(!participants.isEmpty());
    }

    @Test
    public void osteoTwoCountries() {
        DDPInstance instance = DDPInstance.getDDPInstance("Osteo");
        String filter = " AND ( PREQUAL.SELF_COUNTRY = 'US' OR PREQUAL.SELF_COUNTRY = 'CA' )";
        Map<String, Map<String, Object>> participants = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filter);
        Assert.assertTrue(!participants.isEmpty());
    }
}
