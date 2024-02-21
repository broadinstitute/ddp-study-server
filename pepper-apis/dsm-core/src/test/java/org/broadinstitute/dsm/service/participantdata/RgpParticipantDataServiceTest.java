package org.broadinstitute.dsm.service.participantdata;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


@Slf4j
public class RgpParticipantDataServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "rgpservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
    }

    @Test
    public void testCreateFamilyId() {
        String participantId = "PTP123";
        String bookmarkKey = "rgp_family_id";
        long familyId = 1000;

        Bookmark mockBookmark = mock(Bookmark.class);
        when(mockBookmark.getThenIncrementBookmarkValue(bookmarkKey)).thenReturn(familyId);

        RgpFamilyIdProvider familyIdProvider = new RgpFamilyIdProvider(mockBookmark);
        Assert.assertEquals(familyId, familyIdProvider.createFamilyId(participantId));
    }

    @Test
    public void testBuildParticipantData() {
        String participantId = "RGP123";
        long familyId = 1000;
        String collaboratorParticipantId = RgpParticipantDataService.createCollaboratorParticipantId(familyId);
        Profile profile = new Profile();
        profile.setFirstName("Joe");
        profile.setEmail("Joe@broad.org");
        profile.setGuid(participantId);

        Map<String, String> dataMap = RgpParticipantDataService.buildParticipantData(profile, familyId);
        Assert.assertEquals(collaboratorParticipantId, dataMap.get("COLLABORATOR_PARTICIPANT_ID"));
        Assert.assertEquals(String.valueOf(familyId), dataMap.get("FAMILY_ID"));
        Assert.assertEquals("Joe", dataMap.get(FamilyMemberConstants.FIRSTNAME));
        Assert.assertEquals("Joe@broad.org", dataMap.get(FamilyMemberConstants.EMAIL));
    }

    @Test
    public void testCreateDefaultData() {
        String ddpParticipantId = createParticipant();
        loadFieldSettings();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceByGuid(instanceName);
        ElasticSearchParticipantDto esParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);

        int familyId = 1000;
        RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId));

        Map<String, String> expectedDataMap = new HashMap<>();
        expectedDataMap.put("COLLABORATOR_PARTICIPANT_ID",
                RgpParticipantDataService.createCollaboratorParticipantId(familyId));
        // default values per field settings
        expectedDataMap.put("ACTIVE", "ACTIVE");
        expectedDataMap.put("DATA_SHARING", "UNKNOWN");
        expectedDataMap.put("ACCEPTANCE_STATUS", "PRE_REVIEW");
        expectedDataMap.put("REDCAP_SURVEY_TAKER", "NA");
        expectedDataMap.put("FAMILY_ID", Integer.toString(familyId));
        // from profile file
        expectedDataMap.put(FamilyMemberConstants.EMAIL, "SpinkaNortheast@broad.org");

        verifyParticipantData(ddpParticipantId, expectedDataMap);
        verifyDefaultElasticData(ddpParticipantId, familyId, expectedDataMap);
        Set<String> workflowNames = Set.of("REDCAP_SURVEY_TAKER", "ACCEPTANCE_STATUS");
        verifyWorkflows(ddpParticipantId, workflowNames);

        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        RgpParticipantDataService.updateWithExtractedData(ddpParticipantId, instanceName);

        // from profile file
        expectedDataMap.put(FamilyMemberConstants.FIRSTNAME, "Spinka");
        expectedDataMap.put(FamilyMemberConstants.LASTNAME, "Northeast");
        // from activities file
        expectedDataMap.put(FamilyMemberConstants.PHONE, "6177147395");
        expectedDataMap.put(DBConstants.REFERRAL_SOURCE_ID, "MORE_THAN_ONE");

        verifyParticipantData(ddpParticipantId, expectedDataMap);
        verifyDefaultElasticData(ddpParticipantId, familyId, expectedDataMap);
        verifyWorkflows(ddpParticipantId, workflowNames);
    }

    @Test
    public void testInsertEsFamilyId() {
        String ddpParticipantId = createParticipant();
        int familyId = 1000;
        // insert with no Dsm section in ES
        RgpParticipantDataService.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        verifyDefaultElasticData(ddpParticipantId, familyId, Collections.emptyMap());

        // again with Dsm ES section populated with a participant
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);
        ElasticTestUtil.createParticipant(esIndex, participant);

        RgpParticipantDataService.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        verifyDefaultElasticData(ddpParticipantId, familyId, Collections.emptyMap());
    }

    private String createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return ddpParticipantId;
    }


    private void loadFieldSettings() {
        int ddpInstanceId = ddpInstanceDto.getDdpInstanceId();
        int id = FieldSettingsTestUtil.loadOptionsFromFile("fieldsettings/active.json", "RGP_STUDY_STATUS_GROUP",
                "ACTIVE", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsFromFile("fieldsettings/dataSharing.json", "RGP_STUDY_STATUS_GROUP",
                "DATA_SHARING", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsAndActionsFromFile("fieldsettings/acceptanceStatus.json",
                "fieldsettings/acceptanceStatusAction.json", "RGP_STUDY_STATUS_GROUP",
                "ACCEPTANCE_STATUS", ddpInstanceId);
        fieldSettingsIds.add(id);
        id = FieldSettingsTestUtil.loadOptionsAndActionsFromFile("fieldsettings/redcapSurveyTaker.json",
                "fieldsettings/redcapSurveyTakerAction.json", "RGP_SURVEY_GROUP",
                "REDCAP_SURVEY_TAKER", ddpInstanceId);
        fieldSettingsIds.add(id);
    }

    private void verifyDefaultElasticData(String ddpParticipantId, int familyId, Map<String, String> expectedDataMap) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        String esFamilyId = dsm.getFamilyId();
        Assert.assertEquals(Integer.toString(familyId), esFamilyId);

        if (!expectedDataMap.isEmpty()) {
            List<ParticipantData> esParticipantData = dsm.getParticipantData();
            Assert.assertEquals(1, esParticipantData.size());
            ParticipantData participantData = esParticipantData.get(0);
            Assert.assertEquals(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE,
                    participantData.getRequiredFieldTypeId());
            Map<String, String> dataMap = participantData.getDataMap();
            expectedDataMap.forEach((key, value) -> Assert.assertEquals(value, dataMap.get(key)));
        }
    }

    private void verifyWorkflows(String ddpParticipantId, Set<String> workflowNames) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));

        List<Map<String, Object>> workflows = esParticipant.getWorkflows();
        Assert.assertEquals(workflowNames.size(), workflows.size());

        Set<Object> foundWorkflowNames = workflows.stream()
                .map(m -> m.get("workflow")).collect(Collectors.toSet());
        Assert.assertEquals(workflowNames, foundWorkflowNames);
    }

    private void verifyParticipantData(String ddpParticipantId, Map<String, String> expectedDataMap) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantDataByParticipantId(ddpParticipantId);
        Assert.assertEquals(1, ptpDataList.size());

        ParticipantData participantData = ptpDataList.get(0);
        Assert.assertEquals(RgpParticipantDataService.RGP_PARTICIPANTS_FIELD_TYPE,
                participantData.getRequiredFieldTypeId());
        Map<String, String> dataMap = participantData.getDataMap();
        expectedDataMap.forEach((key, value) -> Assert.assertEquals(value, dataMap.get(key)));
    }
}
