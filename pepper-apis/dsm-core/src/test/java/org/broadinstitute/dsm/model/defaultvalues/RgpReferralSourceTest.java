package org.broadinstitute.dsm.model.defaultvalues;

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
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.sort.MockFieldSettingsDao;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class RgpReferralSourceTest {

    private static String refSourceValues;
    private static String refSourceDetails;
    private static MockFieldSettingsDao mockDao;
    private static List<org.broadinstitute.dsm.model.elastic.Activities> activities;

    @BeforeClass
    public static void setup() throws Exception {
        activities = getActivities();

        refSourceValues = TestUtil.readFile("RefSourceFieldSettingsValues.json");
        refSourceDetails = TestUtil.readFile("RefSourceFieldSettingsDetails.json");
        mockDao = new MockFieldSettingsDao();
        FieldSettingsDao.setInstance(mockDao);
    }

    @AfterClass
    public static void tearDown() {
        FieldSettingsDao.setInstance(null);
    }

    @Before
    public void setupFieldSettingsMock() {
        mockDao.refSourceValues = refSourceValues;
        mockDao.refSourceDetails = refSourceDetails;
    }

    @Test
    public void getRefSourceOneProvided() {
        List<String> answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
        Assert.assertEquals("TWITTER", refSourceId);
    }

    @Test
    public void getRefSourceMultipleProvided() {
        List<String> answer = Arrays.asList("TWITTER", "FACEBOOK", "FAMILY");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
        Assert.assertEquals("MORE_THAN_ONE", refSourceId);
    }

    @Test
    public void getRefSourceNoneProvided() {
        List<String> answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        String refSourceId = RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadProvided() {
        List<String> answer = List.of("POSTER");
        applyFoundOutAnswer(activities, answer);
        try {
            RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
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

        String refSourceId = RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(lessActivities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadPossibleValues() throws Exception {
        mockDao.refSourceValues = TestUtil.readFile("RefSourceFieldSettingsBadValues.json");

        // this possible value is still intact
        List<String> answer = List.of("FACEBOOK");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
        Assert.assertEquals("FACEBOOK", refSourceId);

        // the NA is removed
        answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        try {
            RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
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
            RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when there is no corresponding REF_SOURCE for "
                    + "participant provided referral source");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("no corresponding REF_SOURCE"));
        }

        // this answer does not map to a valid REF_SOURCE
        answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        try {
            RgpReferralSource.convertReferralSources(RgpReferralSource.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when participant provided referral source maps to "
                    + "an invalid REF_SOURCE ID");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid REF_SOURCE ID"));
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
}
