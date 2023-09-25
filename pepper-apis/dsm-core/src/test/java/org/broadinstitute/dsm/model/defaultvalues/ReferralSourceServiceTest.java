package org.broadinstitute.dsm.model.defaultvalues;

import static org.broadinstitute.dsm.model.defaultvalues.ReferralSourceService.NA_REF_SOURCE;
import static org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate.isProband;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.sort.MockFieldSettingsDao;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ReferralSourceServiceTest extends DbTxnBaseTest {

    private static final String TEST_USER = "TEST_USER";
    private static String refSourceValues;
    private static String refSourceDetails;
    private static MockFieldSettingsDao mockDao;
    private static ParticipantDataDao participantDataDao;
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static DDPInstanceDto ddpInstanceDto;
    private static List<Activities> activities;
    private static ParticipantData testParticipantData;

    @BeforeClass
    public static void setup() throws Exception {
        activities = getActivities();

        refSourceValues = TestUtil.readFile("RefSourceFieldSettingsValues.json");
        refSourceDetails = TestUtil.readFile("RefSourceFieldSettingsDetails.json");
        mockDao = new MockFieldSettingsDao();
        FieldSettingsDao.setInstance(mockDao);
        String instanceName = String.format("ReferralSourceServiceTest_%d", Instant.now().toEpochMilli());
        ddpInstanceDto = DBTestUtil.createTestDdpInstance(ddpInstanceDao, instanceName);
        participantDataDao = new ParticipantDataDao();
    }

    @AfterClass
    public static void tearDown() {
        FieldSettingsDao.setInstance(null);
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
    }

    @Before
    public void setupFieldSettingsMock() {
        mockDao.refSourceValues = refSourceValues;
        mockDao.refSourceDetails = refSourceDetails;
    }

    @After
    public void deleteParticipants() {
        if (testParticipantData != null) {
            TestParticipantUtil.deleteParticipantData(testParticipantData.getParticipantDataId());
            testParticipantData = null;
        }
    }
    
    @Test
    public void getRefSourceOneProvided() {
        List<String> answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
        Assert.assertEquals("TWITTER", refSourceId);
    }

    @Test
    public void getRefSourceMultipleProvided() {
        List<String> answer = Arrays.asList("TWITTER", "FACEBOOK", "FAMILY");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
        Assert.assertEquals("MORE_THAN_ONE", refSourceId);
    }

    @Test
    public void getRefSourceNoneProvided() {
        List<String> answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        String refSourceId = ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadProvided() {
        List<String> answer = List.of("POSTER");
        applyFoundOutAnswer(activities, answer);
        try {
            ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when bad FOUND_OUT provided");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("no corresponding REF_SOURCE"));
        }
    }

    @Test
    public void getRefSourceNoFindOut() throws Exception {
        List<Activities> lessActivities = getActivities();
        Optional<Activities> enrollActivity = lessActivities.stream().filter(activity ->
                DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode())).findFirst();
        Assert.assertTrue(enrollActivity.isPresent());
        Activities enrollment = enrollActivity.get();

        List<Map<String, Object>> questionsAnswers = enrollment.getQuestionsAnswers();
        Assert.assertNotNull(questionsAnswers);
        List<Map<String, Object>> qa = questionsAnswers.stream()
                .filter(q -> !q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID).equals(
                        DDPActivityConstants.ENROLLMENT_FIND_OUT)).collect(Collectors.toList());
        enrollment.setQuestionsAnswers(qa);
        log.info("Enrollment: " + enrollment);

        String refSourceId = ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(lessActivities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadPossibleValues() throws Exception {
        mockDao.refSourceValues = TestUtil.readFile("RefSourceFieldSettingsBadValues.json");

        // this possible value is still intact
        List<String> answer = List.of("FACEBOOK");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
        Assert.assertEquals("FACEBOOK", refSourceId);

        // the NA is removed
        answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        try {
            ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when possible values NA is missing");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not include a 'NA' key"));
        }
    }

    @Test
    public void getRefSourceBadDetails() throws Exception {
        mockDao.refSourceDetails = TestUtil.readFile("RefSourceFieldSettingsBadDetails.json");

        // this answer is not a valid map key
        List<String> answer = List.of("FACEBOOK");
        applyFoundOutAnswer(activities, answer);
        try {
            ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when there is no corresponding REF_SOURCE for "
                    + "participant provided referral source");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("no corresponding REF_SOURCE"));
        }

        // this answer does not map to a valid REF_SOURCE
        answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        try {
            ReferralSourceService.convertReferralSources(ReferralSourceService.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when participant provided referral source maps to "
                    + "an invalid REF_SOURCE ID");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid REF_SOURCE ID"));
        }
    }

    @Test
    public void updateReferralSourceTest() {
        Gson gson = new Gson();
        ReferralSourceService service = new ReferralSourceService();
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("ReferralSourceServiceTest");
        List<String> answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);

        testParticipantData = createParticipantData(ddpParticipantId, ddpInstanceDto, activities);

        // already has a REFERRAL_SOURCE
        try {
            ReferralSourceService.UpdateStatus res = service.updateReferralSource(ddpParticipantId,
                    List.of(testParticipantData), activities);
            Assert.assertEquals(ReferralSourceService.UpdateStatus.NOT_UPDATED, res);
        } catch (Exception e) {
            Assert.fail("Exception from updateReferralSource: " + e);
        }

        // does not already have a REFERRAL_SOURCE but ptp is not proband
        Map<String, String> dataMap = ReferralSourceService.getDataMap(testParticipantData);
        dataMap.remove(DBConstants.REFERRAL_SOURCE_ID);
        dataMap.remove(FamilyMemberConstants.MEMBER_TYPE);
        testParticipantData.setData(gson.toJson(dataMap));
        updateParticipantData(testParticipantData);
        try {
            ReferralSourceService.UpdateStatus res = service.updateReferralSource(ddpParticipantId,
                    List.of(testParticipantData), activities);
            Assert.assertEquals(ReferralSourceService.UpdateStatus.NOT_UPDATED, res);
        } catch (Exception e) {
            Assert.fail("Exception from updateReferralSource: " + e);
        }

        // has a non-NA REFERRAL_SOURCE and ptp is proband
        dataMap.put(FamilyMemberConstants.MEMBER_TYPE, FamilyMemberConstants.MEMBER_TYPE_SELF);
        dataMap.put(DBConstants.REFERRAL_SOURCE_ID, "TWITTER");
        testParticipantData.setData(gson.toJson(dataMap));
        updateParticipantData(testParticipantData);
        try {
            ReferralSourceService.UpdateStatus res = service.updateReferralSource(ddpParticipantId,
                    List.of(testParticipantData), activities);
            Assert.assertEquals(ReferralSourceService.UpdateStatus.NOT_UPDATED, res);
        } catch (Exception e) {
            Assert.fail("Exception from updateReferralSource: " + e);
        }

        // has a NA REFERRAL_SOURCE and ptp is proband
        dataMap.put(DBConstants.REFERRAL_SOURCE_ID, NA_REF_SOURCE);
        testParticipantData.setData(gson.toJson(dataMap));
        updateParticipantData(testParticipantData);
        try {
            ReferralSourceService.UpdateStatus res = service.updateReferralSource(ddpParticipantId,
                    List.of(testParticipantData), activities);
            Assert.assertEquals(ReferralSourceService.UpdateStatus.UPDATED, res);
            verifyReferralSource(testParticipantData.getParticipantDataId(), "TWITTER");
        } catch (Exception e) {
            Assert.fail("Exception from updateReferralSource: " + e);
        }

        // multiple participant data records for ptp
        ParticipantData secondRecord = createParticipantData(ddpParticipantId, ddpInstanceDto, activities);
        dataMap = ReferralSourceService.getDataMap(secondRecord);
        dataMap.remove(DBConstants.REFERRAL_SOURCE_ID);
        secondRecord.setData(gson.toJson(dataMap));
        updateParticipantData(secondRecord);
        try {
            ReferralSourceService.UpdateStatus res = service.updateReferralSource(ddpParticipantId,
                    List.of(testParticipantData, secondRecord), activities);
            Assert.assertEquals(ReferralSourceService.UpdateStatus.UPDATED, res);
            verifyReferralSource(testParticipantData.getParticipantDataId(), "TWITTER");
            verifyReferralSource(secondRecord.getParticipantDataId(), "TWITTER");
        } catch (Exception e) {
            Assert.fail("Exception from updateReferralSource: " + e);
        } finally {
            TestParticipantUtil.deleteParticipantData(secondRecord.getParticipantDataId());
        }
    }

    private void applyFoundOutAnswer(List<Activities> activities, List<String> answer) {
        Optional<Activities> enrollment = activities.stream().filter(activity ->
                DDPActivityConstants.ACTIVITY_ENROLLMENT.equals(activity.getActivityCode())).findFirst();
        Assert.assertTrue(enrollment.isPresent());

        List<Map<String, Object>> questionsAnswers = enrollment.get().getQuestionsAnswers();
        Assert.assertNotNull(questionsAnswers);
        Optional<Map<String, Object>> rs = questionsAnswers.stream()
                .filter(q -> q.get(DDPActivityConstants.DDP_ACTIVITY_STABLE_ID).equals(
                        DDPActivityConstants.ENROLLMENT_FIND_OUT))
                .findFirst();
        Assert.assertTrue(rs.isPresent());
        Map<String, Object> refSourceQA = rs.get();

        refSourceQA.put(DDPActivityConstants.ACTIVITY_QUESTION_ANSWER, answer);
    }

    protected static List<Activities> getActivities() {
        List<Activities> activitiesList = new ArrayList<>();
        try {
            String json = TestUtil.readFile("activities.json");
            JsonArray jsonArray = (JsonArray) JsonParser.parseString(json);
            Assert.assertNotNull(jsonArray);

            Gson gson = new Gson();
            jsonArray.forEach(a -> activitiesList.add(gson.fromJson(a.getAsJsonObject(), Activities.class)));
        } catch (Exception e) {
            Assert.fail("Error loading activities fixture: " + e);
        }
        return activitiesList;
    }

    private ParticipantData createParticipantData(String ddpParticipantId, DDPInstanceDto instance, List<Activities> activities) {
        String instanceName = instance.getInstanceName();
        Profile esProfile = new Profile();
        esProfile.setGuid(ddpParticipantId);
        esProfile.setEmail("test_ptp@gmail.com");
        esProfile.setLastName("test_ptp_last_name");

        // this includes referral source from activities
        Map<String, String> dataMap = RgpAutomaticProbandDataCreator.buildDataMap(ddpParticipantId, 1234L, instanceName,
                activities, esProfile);

        Map<String, String> participantDefaults = getParticipantDefaults(instance.getDdpInstanceId());
        participantDefaults.forEach(dataMap::putIfAbsent);

        return TestParticipantUtil.createParticipantData(ddpParticipantId,
                dataMap, ReferralSourceService.RGP_PARTICIPANT_DATA, instance.getDdpInstanceId(), TEST_USER);
    }

    private void updateParticipantData(ParticipantData participantData) {
        participantDataDao.updateParticipantDataColumn(
                new ParticipantData.Builder()
                        .withParticipantDataId(participantData.getParticipantDataId())
                        .withDdpParticipantId(participantData.getRequiredDdpParticipantId())
                        .withDdpInstanceId(participantData.getDdpInstanceId())
                        .withFieldTypeId(participantData.getFieldTypeId().orElseThrow())
                        .withData(participantData.getData().orElseThrow())
                        .withLastChanged(System.currentTimeMillis())
                        .withChangedBy(TEST_USER).build());
    }

    private static Map<String, String> getParticipantDefaults(int ddpInstanceId) {
        List<FieldSettingsDto> fieldSettingsList =
                FieldSettingsDao.of().getOptionAndRadioFieldSettingsByInstanceId(ddpInstanceId);
        FieldSettings fieldSettings = new FieldSettings();
        return fieldSettings.getColumnsWithDefaultValues(fieldSettingsList);
    }

    private static void verifyReferralSource(int participantDataId, String referralSource) {
        Optional<ParticipantData> participantData = participantDataDao.get(participantDataId);
        Assert.assertTrue(participantData.isPresent());
        Map<String, String> dataMap = ReferralSourceService.getDataMap(participantData.get());
        Assert.assertTrue(isProband(dataMap));
        Assert.assertTrue(dataMap.containsKey(DBConstants.REFERRAL_SOURCE_ID));
        Assert.assertEquals(dataMap.get(DBConstants.REFERRAL_SOURCE_ID), referralSource);
    }
}
