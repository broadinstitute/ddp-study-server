package org.broadinstitute.dsm.model.defaultvalues;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.service.adminoperation.ReferralSourceServiceTest;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


@Slf4j
public class RgpAutomaticProbandDataCreatorTest extends DbAndElasticBaseTest {
    private static final String instanceName = "rgpproband";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;

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
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
    }

    @Test
    public void getFamilyId() {
        String participantId = "PTP123";
        String instanceId = "rgp_family_id";
        long familyId = 1000;
        int bookmarkId = 1;

        BookmarkDto bookmarkDto = new BookmarkDto.Builder(familyId, instanceId).withBookmarkId(bookmarkId).build();
        BookmarkDao bookmarkDao = mock(BookmarkDao.class);
        when(bookmarkDao.getBookmarkByInstance(instanceId)).thenReturn(Optional.of(bookmarkDto));
        when(bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkId, familyId)).thenReturn(1);
        Bookmark bookmark = new Bookmark(bookmarkDao);

        Assert.assertEquals(familyId, RgpAutomaticProbandDataCreator.getFamilyId(participantId, bookmark));

        when(bookmarkDao.getBookmarkByInstance(instanceId)).thenThrow(new RuntimeException("Error getting bookmark with instance"));
        try {
            RgpAutomaticProbandDataCreator.getFamilyId(participantId, bookmark);
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("not found in Bookmark table"));
        }
    }

    @Test
    public void buildDataMap() {
        String participantId = "RGP123";
        long familyId = 1000;
        String instanceName = "rgp";
        String collaboratorParticipantId =
                instanceName.toUpperCase() + "_" + familyId + "_" + FamilyMemberConstants.PROBAND_RELATIONSHIP_ID;
        Profile profile = new Profile();
        profile.setFirstName("Joe");
        profile.setEmail("Joe@broad.org");
        profile.setGuid(participantId);
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        List<Activities> activities = ReferralSourceServiceTest.getActivities();
        Map<String, String> dataMap = dataCreator.buildDataMap(participantId, familyId, instanceName,
                activities, profile);
        Assert.assertEquals(collaboratorParticipantId, dataMap.get("COLLABORATOR_PARTICIPANT_ID"));
        Assert.assertEquals(String.valueOf(familyId), dataMap.get("FAMILY_ID"));
        // from activities FILLER_PHONE
        Assert.assertEquals("6177147395", dataMap.get("DATSTAT_MOBILEPHONE"));
        Assert.assertEquals("MORE_THAN_ONE", dataMap.get("REF_SOURCE"));
    }

    @Test
    public void testInsertEsFamilyId() {
        String ddpParticipantId = createParticipant();
        int familyId = 1000;
        // insert with no Dsm section in ES
        RgpAutomaticProbandDataCreator.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        verifyDefaultElasticData(ddpParticipantId, familyId);

        // again with Dsm ES section populated with a participant
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);
        ElasticTestUtil.createParticipant(esIndex, participant);

        RgpAutomaticProbandDataCreator.insertEsFamilyId(esIndex, ddpParticipantId, familyId);
        verifyDefaultElasticData(ddpParticipantId, familyId);
    }

    private String createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return ddpParticipantId;
    }

    private void verifyDefaultElasticData(String ddpParticipantId, int familyId) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        String esFamilyId = dsm.getFamilyId();
        Assert.assertEquals(Integer.toString(familyId), esFamilyId);
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

    private static List<Activities> getActivities() throws Exception {
        String json = TestUtil.readFile("activities.json");
        JsonArray jsonArray = (JsonArray) JsonParser.parseString(json);
        Assert.assertNotNull(jsonArray);

        List<Activities> activitiesList = new ArrayList<>();
        Gson gson = new Gson();
        jsonArray.forEach(a -> activitiesList.add(gson.fromJson(a.getAsJsonObject(), Activities.class)));
        return activitiesList;
    }
}
