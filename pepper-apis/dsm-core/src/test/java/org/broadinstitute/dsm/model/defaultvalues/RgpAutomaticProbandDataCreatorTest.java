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
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.sort.MockFieldSettingsDao;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


public class RgpAutomaticProbandDataCreatorTest extends DbTxnBaseTest {

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

        List<Activities> activities = RgpReferralSourceTest.getActivities();
        Map<String, String> dataMap = dataCreator.buildDataMap(participantId, familyId, instanceName,
                activities, profile);
        Assert.assertEquals(collaboratorParticipantId, dataMap.get("COLLABORATOR_PARTICIPANT_ID"));
        Assert.assertEquals(String.valueOf(familyId), dataMap.get("FAMILY_ID"));
        // from activities FILLER_PHONE
        Assert.assertEquals("6177147395", dataMap.get("DATSTAT_MOBILEPHONE"));
        Assert.assertEquals("MORE_THAN_ONE", dataMap.get("REF_SOURCE"));
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
