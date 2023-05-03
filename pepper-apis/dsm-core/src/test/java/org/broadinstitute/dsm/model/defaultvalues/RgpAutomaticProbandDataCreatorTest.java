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
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.sort.MockFieldSettingsDao;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RgpAutomaticProbandDataCreatorTest {

    private static final Logger logger = LoggerFactory.getLogger(RgpAutomaticProbandDataCreatorTest.class);
    private static List<org.broadinstitute.dsm.model.elastic.Activities> activities;
    private static String refSourceValues;
    private static String refSourceDetails;
    private static MockFieldSettingsDao mockDao;

    @BeforeClass
    public static void setup() throws Exception {
        activities = RgpAutomaticProbandDataCreatorTest.getActivities();

        refSourceValues = TestUtil.readFile("RefSourceFieldSettingsValues.json");
        refSourceDetails = TestUtil.readFile("RefSourceFieldSettingsDetails.json");
        mockDao = new MockFieldSettingsDao();
        FieldSettingsDao.setInstance(mockDao);
    }

    @Before
    public void setupFieldSettingsMock() {
        mockDao.refSourceValues = refSourceValues;
        mockDao.refSourceDetails = refSourceDetails;
    }

    @Test
    public void getRefSourceOneProvided() throws Exception {
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        List<String> answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
        Assert.assertEquals("TWITTER", refSourceId);
    }

    @Test
    public void getRefSourceMultipleProvided() {
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        List<String> answer = Arrays.asList("TWITTER", "FACEBOOK", "FAMILY");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
        Assert.assertEquals("MORE_THAN_ONE", refSourceId);
    }

    @Test
    public void getRefSourceNoneProvided() {
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        List<String> answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        String refSourceId = dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadProvided() {
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        List<String> answer = List.of("POSTER");
        applyFoundOutAnswer(activities, answer);
        try {
            dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when bad FOUND_OUT provided");
        } catch (RuntimeException e) {
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
        logger.info("Enrollment: " + enrollment);

        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();
        String refSourceId = dataCreator.convertReferralSources(dataCreator.getReferralSources(lessActivities));
        Assert.assertEquals("NA", refSourceId);
    }

    @Test
    public void getRefSourceBadPossibleValues() throws Exception {
        mockDao.refSourceValues = TestUtil.readFile("RefSourceFieldSettingsBadValues.json");
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        // this possible value is still intact
        List<String> answer = List.of("FACEBOOK");
        applyFoundOutAnswer(activities, answer);
        String refSourceId = dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
        Assert.assertEquals("FACEBOOK", refSourceId);

        // the NA is removed
        answer = new ArrayList<>();
        applyFoundOutAnswer(activities, answer);
        try {
            dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when possible values NA is missing");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("does not include a 'NA' key"));
        }
    }

    @Test
    public void getRefSourceBadDetails() throws Exception {
        mockDao.refSourceDetails = TestUtil.readFile("RefSourceFieldSettingsBadDetails.json");
        RgpAutomaticProbandDataCreator dataCreator = new RgpAutomaticProbandDataCreator();

        // this answer is not a valid map key
        List<String> answer = List.of("FACEBOOK");
        applyFoundOutAnswer(activities, answer);
        try {
            dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when there is no corresponding REF_SOURCE for "
                    + "participant provided referral source");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("no corresponding REF_SOURCE"));
        }

        // this answer does not map to a valid REF_SOURCE
        answer = List.of("TWITTER");
        applyFoundOutAnswer(activities, answer);
        try {
            dataCreator.convertReferralSources(dataCreator.getReferralSources(activities));
            Assert.fail("convertReferralSources should throw when participant provided referral source maps to "
                    + "an invalid REF_SOURCE ID");
        } catch (RuntimeException e) {
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
