package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.StatisticsConfigurationDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.statistics.StatisticsFigure;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.statistics.StatisticsType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetStudyStatisticsRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Auth0Util.TestingUser testUser2;
    private static Auth0Util.TestingUser testUser3;
    private static ActivityInstanceDto instance1Dto;
    private static ActivityInstanceDto instance2Dto;
    private static ActivityInstanceDto instance3Dto;
    private static String user1Guid;
    private static String user2Guid;
    private static String user3Guid;
    private static String token;
    private static String url;
    private static Gson gson;
    private static MailAddress mailAddress;

    @BeforeClass
    public static void setup() throws Exception {
        gson = new Gson();
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            testUser2 = TestDataSetupUtil.generateTestUser(handle, testData.getStudyGuid());
            testUser3 = TestDataSetupUtil.generateTestUser(handle, testData.getStudyGuid());
            token = testData.getTestingUser().getToken();
            user1Guid = testData.getUserGuid();
            user2Guid = testUser2.getUserGuid();
            user3Guid = testUser3.getUserGuid();
            mailAddress = createTestAddress(handle, user3Guid);
            setupData(handle);
        });
        String endpoint = API.STUDY_STATISTICS
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static MailAddress createTestAddress(Handle handle, String userGuid) {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Richard Crenna");
        testAddress.setStreet1("415 Main Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setCountry("US");
        testAddress.setZip("02142");
        testAddress.setPhone("999-232-5522");
        testAddress.setDefault(true);
        Config cfg = ConfigManager.getInstance().getConfig();
        AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));
        return addressService.addAddress(handle, testAddress, userGuid, userGuid);
    }

    private static void setupData(Handle handle) {
        PicklistQuestionDef p1 = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.LIST, "PL", newTemplate())
                .addOption(new PicklistOptionDef("PL_OPT_1", newTemplate()))
                .addOption(new PicklistOptionDef("PL_OPT_2", newTemplate()))
                .build();
        FormSectionDef plistSection = new FormSectionDef(null, TestUtil.wrapQuestions(p1));

        String activityCode = "ACTIVITY_" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activityCode))
                .addSections(Collections.singletonList(plistSection))
                .build();

        handle.attach(ActivityDao.class).insertActivity(
                activity, RevisionMetadata.now(testData.getUserId(), "add " + activityCode)
        );
        assertNotNull(activity.getActivityId());
        instance1Dto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), user1Guid);
        instance2Dto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), user2Guid);
        instance3Dto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), user3Guid);

        // Answering the question for 3 users
        AnswerDao answerDao = handle.attach(AnswerDao.class);
        answerDao.createAnswer(testData.getUserId(), instance1Dto.getId(),
                new PicklistAnswer(null, "PL", null,
                        Collections.singletonList(new SelectedPicklistOption("PL_OPT_1"))));
        answerDao.createAnswer(testUser2.getUserId(), instance2Dto.getId(),
                new PicklistAnswer(null, "PL", null,
                        Collections.singletonList(new SelectedPicklistOption("PL_OPT_2"))));
        answerDao.createAnswer(testUser3.getUserId(), instance3Dto.getId(),
                new PicklistAnswer(null, "PL", null,
                        Collections.singletonList(new SelectedPicklistOption("PL_OPT_1"))));

        // Finishing enrollment for 2 users
        JdbiUserStudyEnrollment userStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
        userStudyEnrollment.changeUserStudyEnrollmentStatus(
                user1Guid,
                testData.getStudyGuid(),
                EnrollmentStatusType.ENROLLED);
        userStudyEnrollment.changeUserStudyEnrollmentStatus(
                user2Guid,
                testData.getStudyGuid(),
                EnrollmentStatusType.ENROLLED);

        // Configuring study statistics
        StatisticsConfigurationDao statConfigDao = handle.attach(StatisticsConfigurationDao.class);
        statConfigDao.insertConfiguration(testData.getStudyId(), StatisticsType.DISTRIBUTION, "PL", null);
        statConfigDao.insertConfiguration(testData.getStudyId(), StatisticsType.KITS, null, null);
        statConfigDao.insertConfiguration(testData.getStudyId(), StatisticsType.MAILING_LIST, null, null);
        statConfigDao.insertConfiguration(testData.getStudyId(), StatisticsType.SPECIFIC_ANSWER, "PL", "PL_OPT_1");
        statConfigDao.insertConfiguration(testData.getStudyId(), StatisticsType.PARTICIPANTS, null, null);

        // Setting up a governance
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        long governanceId = userGovernanceDao.assignProxy("alias", testUser3.getUserId(), testUser2.getUserId());
        userGovernanceDao.grantGovernedStudy(governanceId, testData.getStudyId());

        // Requesting a kit
        DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
        KitType salivaKitType = handle.attach(KitTypeDao.class).getSalivaKitType();
        kitDao.createKitRequest(testData.getStudyGuid(), mailAddress, testUser3.getUserId(), salivaKitType);

        // Subscribing
        JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);
        jdbiMailingList.insertByStudyGuidIfNotStoredAlready("John", "Doe", "john@doe.com",
                testData.getStudyGuid(), null, Instant.now().toEpochMilli());
    }

    private static Template newTemplate() {
        return new Template(TemplateType.TEXT, null, "template " + Instant.now().toEpochMilli());
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
            activityInstanceDao.deleteAllByIds(Set.of(instance1Dto.getId()));
            activityInstanceDao.deleteAllByIds(Set.of(instance2Dto.getId()));
            activityInstanceDao.deleteAllByIds(Set.of(instance3Dto.getId()));
        });
    }

    @Test
    public void testUnauthorized() throws Exception {
        HttpResponse response = Request.Get(url).execute().returnResponse();
        assertEquals(401, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, resp.getCode());
    }

    @Test
    public void testStatisticsData() throws Exception {
        HttpResponse response = RouteTestUtil.buildAuthorizedGetRequest(token, url).execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        Type type = new TypeToken<List<StatisticsFigure>>() {}.getType();
        List<StatisticsFigure> statisticsFigures = gson.fromJson(json, type);
        for (StatisticsFigure statisticsFigure : statisticsFigures) {
            switch (statisticsFigure.getConfiguration().getType()) {
                case DISTRIBUTION:
                    assertEquals("DISTRIBUTION statistics: answers count doesn't match",
                            "1", statisticsFigure.getStatistics().get(0).getData().get("count"));
                    assertEquals("DISTRIBUTION statistics: answers count doesn't match",
                            "1", statisticsFigure.getStatistics().get(1).getData().get("count"));
                    assertNotNull("DISTRIBUTION statistics: optionDetails missing",
                            statisticsFigure.getStatistics().get(0).getData().get("optionDetails"));
                    break;
                case SPECIFIC_ANSWER:
                    assertEquals("SPECIFIC_ANSWER statistics: answers count doesn't match",
                            "1", statisticsFigure.getStatistics().get(0).getData().get("count"));
                    break;
                case PARTICIPANTS:
                    assertEquals("PARTICIPANTS statistics: participants count doesn't match",
                            "1", statisticsFigure.getStatistics().get(0).getData().get("count"));
                    assertEquals("PARTICIPANTS statistics: participants count doesn't match",
                            "1", statisticsFigure.getStatistics().get(1).getData().get("count"));
                    break;
                case KITS:
                    assertEquals("KITS statistics: types count doesn't match",
                            1, statisticsFigure.getStatistics().size());
                    assertEquals("KITS statistics: count of kits request doesn't match",
                            "1", statisticsFigure.getStatistics().get(0).getData().get("count"));
                    break;
                case MAILING_LIST:
                    assertEquals("MAILING_LIST statistics: subscription count doesn't match",
                            "1", statisticsFigure.getStatistics().get(0).getData().get("count"));
                    break;
                default:
                    fail("Unrecognized statistics type " + statisticsFigure.getConfiguration().getType());
            }
        }
    }
}
