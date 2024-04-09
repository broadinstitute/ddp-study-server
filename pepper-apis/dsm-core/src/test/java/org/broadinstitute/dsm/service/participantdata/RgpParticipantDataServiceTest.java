package org.broadinstitute.dsm.service.participantdata;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
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
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String instanceName = "rgpservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static RgpParticipantDataTestUtil rgpParticipantDataTestUtil;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        rgpParticipantDataTestUtil = new RgpParticipantDataTestUtil(esIndex);
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
        rgpParticipantDataTestUtil.tearDown();
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
        // this dictates which default values and workflows are set
        rgpParticipantDataTestUtil.loadFieldSettings(ddpInstanceDto.getDdpInstanceId());

        DDPInstance ddpInstance = DDPInstance.getDDPInstanceByGuid(instanceName);
        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocumentById(ddpParticipantId, esIndex);

        int familyId = 1000;
        RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipantDto, ddpInstance,
                new TestFamilyIdProvider(familyId));

        Set<String> workflowNames = rgpParticipantDataTestUtil.verifyDefaultData(ddpParticipantId, familyId);

        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        RgpParticipantDataService.updateWithExtractedData(ddpParticipantId, instanceName);

        Map<String, String> expectedDataMap = new HashMap<>();
        // from profile file
        expectedDataMap.put(FamilyMemberConstants.FIRSTNAME, "Spinka");
        expectedDataMap.put(FamilyMemberConstants.LASTNAME, "Northeast");
        // from activities file
        expectedDataMap.put(FamilyMemberConstants.PHONE, "6177147395");
        expectedDataMap.put(DBConstants.REFERRAL_SOURCE_ID, "MORE_THAN_ONE");

        RgpParticipantDataTestUtil.verifyParticipantData(ddpParticipantId, expectedDataMap);
        rgpParticipantDataTestUtil.verifyDefaultElasticData(ddpParticipantId, familyId, expectedDataMap);
        rgpParticipantDataTestUtil.verifyWorkflows(ddpParticipantId, workflowNames);
    }

    @Test
    public void testInsertEsFamilyId() {
        String ddpParticipantId = createParticipant();
        int familyId = 1000;
        // insert with no Dsm section in ES
        RgpParticipantDataService.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        rgpParticipantDataTestUtil.verifyDefaultElasticData(ddpParticipantId, familyId, Collections.emptyMap());

        // again with Dsm ES section populated with a participant
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);
        ElasticTestUtil.createParticipant(esIndex, participant);

        RgpParticipantDataService.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        rgpParticipantDataTestUtil.verifyDefaultElasticData(ddpParticipantId, familyId, Collections.emptyMap());

        try {
            // ptp who does not have an ES document
            RgpParticipantDataService.insertEsFamilyId(esIndex, "bogus", familyId);
            Assert.fail("Expected exception not thrown");
        } catch (Exception e) {
            Assert.assertEquals(ESMissingParticipantDataException.class, e.getClass());
            Assert.assertTrue(e.getMessage().contains("Participant document"));
        }
    }

    private String createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return ddpParticipantId;
    }
}
