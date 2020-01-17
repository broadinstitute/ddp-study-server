package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventActionSql;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiActivityStatusTrigger;
import org.broadinstitute.ddp.db.dao.JdbiEventAction;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiEventTrigger;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.json.AnswerSubmission;
import org.broadinstitute.ddp.json.PatchAnswerPayload;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventServiceTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(EventServiceTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;
    private long dsmInclusionEventActionId;
    private long studyActivityToCreateId;
    private long activityInstanceCreationEventActionId;
    private long eventTriggerId;
    private long studyActivityTriggeringActionId;
    private long umbrellaStudyId;
    private long creationExprId;
    private long precondExprId;
    private long autoInstantiationConfigurationId;
    private long updateDSMInclusionConfigurationId;
    private long testUserId;
    private long announcementActionId;
    private long announcementConfigId;
    private long announcementMsgTemplateId;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            setupTestActivityData(handle);
        });
        String endpoint = API.USER_ACTIVITY_ANSWERS
                .replace(PathParam.USER_GUID, testData.getUserGuid())
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(PathParam.INSTANCE_GUID, TestData.activityInstanceGuid);
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupTestActivityData(Handle handle) {
        // Create an activity that triggers instantiation.
        TestData.boolStableId = "BOOL_Q_IN_ACT_TRIGGER_" + Instant.now().toEpochMilli();
        BoolQuestionDef question = BoolQuestionDef
                .builder(TestData.boolStableId, Template.text("prompt"), Template.text("yes"), Template.text("no"))
                .build();
        TestData.sourceActivityCode = "ACT_THAT_TRIGGERS_" + Instant.now().toEpochMilli();
        FormActivityDef act = FormActivityDef
                .generalFormBuilder(TestData.sourceActivityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + TestData.sourceActivityCode))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question)))
                .build();
        handle.attach(ActivityDao.class).insertActivity(act,
                RevisionMetadata.now(testData.getUserId(), "test auto activity instantiation"));
        assertNotNull(act.getActivityId());

        // Create an activity instance for it.
        ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(act.getActivityId(), testData.getUserGuid());
        TestData.activityInstanceGuid = instanceDto.getGuid();
        TestData.activityInstanceId = instanceDto.getId();

        // Create an activity that gets an instance created for by trigger action.
        TestData.targetActivityCode = "ACT_TO_CREATE_" + Instant.now().toEpochMilli();
        FormActivityDef actToCreate = FormActivityDef
                .generalFormBuilder(TestData.targetActivityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + TestData.targetActivityCode))
                .build();
        handle.attach(ActivityDao.class).insertActivity(actToCreate,
                RevisionMetadata.now(testData.getUserId(), "test auto activity instantiation"));
        assertNotNull(actToCreate.getActivityId());

        // Create a pex expression used in tests.
        Expression expr = handle.attach(JdbiExpression.class).insertExpression("true");
        TestData.expressionGuid = expr.getGuid();
    }

    @Before
    public void setupTheTest() {
        TransactionWrapper.useTxn(
                handle -> {
                    testUserId = testData.getUserId();
                    umbrellaStudyId = testData.getStudyId();
                    long timestamp = Instant.now().toEpochMilli();
                    EventActionDao eventActionDao = handle.attach(EventActionDao.class);
                    JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

                    studyActivityToCreateId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(umbrellaStudyId,
                            TestData.targetActivityCode).get();
                    activityInstanceCreationEventActionId = eventActionDao.insertInstanceCreationAction(studyActivityToCreateId, null);

                    dsmInclusionEventActionId = eventActionDao.insertEnrolledAction();

                    long revId = handle.attach(JdbiRevision.class).insertStart(timestamp, testUserId, "test announcements");
                    announcementMsgTemplateId = handle.attach(TemplateDao.class).insertTemplate(Template.html("<b>thank you!</b>"), revId);
                    announcementActionId = eventActionDao.insertAnnouncementAction(announcementMsgTemplateId, false, false);

                    studyActivityTriggeringActionId = handle.attach(JdbiActivity.class)
                            .findIdByStudyIdAndCode(umbrellaStudyId, TestData.sourceActivityCode).get();
                    eventTriggerId = handle.attach(EventTriggerDao.class)
                            .insertStatusTrigger(studyActivityTriggeringActionId, InstanceStatusType.IN_PROGRESS);

                    precondExprId = handle.attach(JdbiExpression.class).getByGuid(TestData.expressionGuid).get().getId();
                    creationExprId = handle.attach(JdbiExpression.class).insertExpression("true").getId();
                    autoInstantiationConfigurationId = jdbiEventConfig.insert(
                            eventTriggerId,
                            activityInstanceCreationEventActionId,
                            umbrellaStudyId,
                            timestamp,
                            5,
                            null,
                            precondExprId,
                            null,
                            false,
                            1
                    );

                    updateDSMInclusionConfigurationId = jdbiEventConfig.insert(
                            eventTriggerId,
                            dsmInclusionEventActionId,
                            umbrellaStudyId,
                            timestamp,
                            null,
                            null,
                            precondExprId,
                            null,
                            false,
                            1
                    );

                    announcementConfigId = jdbiEventConfig.insert(
                            eventTriggerId,
                            announcementActionId,
                            umbrellaStudyId,
                            timestamp,
                            1,
                            null,
                            null,
                            null,
                            false,
                            1
                    );

                    // Resetting the activity instance status to "CREATED", otherwise the status change won't occur
                    long activityInstanceId = handle.attach(JdbiActivityInstance.class)
                            .getActivityInstanceId(TestData.activityInstanceGuid);
                    handle.attach(JdbiActivityInstanceStatus.class).insert(
                            activityInstanceId,
                            InstanceStatusType.CREATED,
                            timestamp,
                            testUserId
                    );

                    // Start fresh with no announcements.
                    handle.attach(UserAnnouncementDao.class).deleteAllForUserAndStudy(testUserId, umbrellaStudyId);

                    TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.REGISTERED);
                }
        );
    }

    @After
    public void teardownTheTest() {
        TransactionWrapper.useTxn(
                handle -> {
                    // Required because apparently Housekeeping manages to insert a row in background
                    QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                    queuedEventDao.deleteQueuedEventsByEventConfigurationId(updateDSMInclusionConfigurationId);
                    queuedEventDao.deleteQueuedEventsByEventConfigurationId(autoInstantiationConfigurationId);
                    handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteAll();
                    handle.attach(JdbiEventConfiguration.class).deleteById(autoInstantiationConfigurationId);
                    handle.attach(JdbiEventConfiguration.class).deleteById(updateDSMInclusionConfigurationId);
                    handle.attach(JdbiEventConfiguration.class).deleteById(announcementConfigId);
                    handle.attach(JdbiActivityStatusTrigger.class).deleteById(eventTriggerId);
                    handle.attach(JdbiEventTrigger.class).deleteById(eventTriggerId);
                    handle.attach(EventActionSql.class).deleteActivityInstanceCreationAction(activityInstanceCreationEventActionId);
                    handle.attach(JdbiEventAction.class).deleteById(activityInstanceCreationEventActionId);
                    handle.attach(JdbiEventAction.class).deleteById(dsmInclusionEventActionId);
                    handle.attach(EventActionDao.class).deleteAnnouncementAction(announcementActionId);
                    handle.attach(JdbiExpression.class).deleteById(creationExprId);
                    handle.attach(JdbiExpression.class).updateById(precondExprId, "true");
                    handle.attach(UserAnnouncementDao.class).deleteAllForUserAndStudy(testUserId, umbrellaStudyId);
                    TestDataSetupUtil.deleteEnrollmentStatus(handle, testData);
                }
        );
    }

    @Test
    public void testEventNotExecutedAgainWhenCounterIsReached() throws IOException {
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        // Do another triggering
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusChangeSignal signal = new ActivityInstanceStatusChangeSignal(
                    testUserId, testUserId, testData.getUserGuid(),
                    TestData.activityInstanceId, studyActivityTriggeringActionId,
                    umbrellaStudyId, InstanceStatusType.IN_PROGRESS);
            EventService.getInstance().processAllActionsForEventSignal(handle, signal);
        });

        List<UserAnnouncement> res = TransactionWrapper.withTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .findAllForUserAndStudy(testUserId, umbrellaStudyId)
                .collect(Collectors.toList()));

        assertEquals("there should only be one announcement created", 1, res.size());
        assertEquals(testUserId, res.get(0).getUserId());
        assertEquals(umbrellaStudyId, res.get(0).getStudyId());
        assertEquals(announcementMsgTemplateId, res.get(0).getMsgTemplateId());
    }

    @Test
    public void testAnnouncementAction_messageGetsAdded() throws IOException {
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        List<UserAnnouncement> res = TransactionWrapper.withTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .findAllForUserAndStudy(testUserId, umbrellaStudyId)
                .collect(Collectors.toList()));

        assertEquals(1, res.size());
        assertEquals(testUserId, res.get(0).getUserId());
        assertEquals(umbrellaStudyId, res.get(0).getStudyId());
        assertEquals(announcementMsgTemplateId, res.get(0).getMsgTemplateId());
    }

    @Test
    public void testDSMInclusion_Success() throws Exception {
        long timeToCheck = Instant.now().toEpochMilli();
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        List<EnrollmentStatusDto> usersReturned = TransactionWrapper
                .withTxn(handle -> (handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(testData.getStudyGuid(), timeToCheck)));

        assertTrue(usersReturned.stream()
                .map(user -> user.getUserId())
                .collect(Collectors.toList()).contains(testUserId));
    }

    @Test
    public void testActivityInstantiation_Success() throws Exception {
        int countBefore = TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivityInstance.class).getCount());
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        int countAfter = TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivityInstance.class).getCount());
        Assert.assertTrue(countAfter > countBefore);
    }

    @Test
    public void testActivityInstantiation_Failure_PreconditionExpressionFalse() throws Exception {
        TransactionWrapper.useTxn(handle -> handle.attach(JdbiExpression.class).updateById(precondExprId, "false"));
        int countBefore = getNumActivityInstances();
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        int countAfter = getNumActivityInstances();
        Assert.assertTrue(countAfter == countBefore);
    }

    @Test
    public void testActivityInstantiation_Failure_maxInstancesPerUserExceeded() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiActivity.class).updateMaxInstancesPerUserById(
                            studyActivityToCreateId,
                            1
                    );
                }
        );
        String actInstGuid = TransactionWrapper.withTxn(
                handle -> {
                    return handle.attach(ActivityInstanceDao.class)
                            .insertInstance(studyActivityToCreateId, testData.getUserGuid())
                            .getGuid();
                }
        );
        int countBefore = getNumActivityInstances();
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        int countAfter = getNumActivityInstances();
        Assert.assertTrue(countAfter == countBefore);
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(actInstGuid);
                    handle.attach(JdbiActivity.class).updateMaxInstancesPerUserById(
                            studyActivityToCreateId,
                            null
                    );
                }
        );
    }

    @Test
    public void testActivityInstantiation_Failure_maxOccurrencesPerUserExceeded() throws Exception {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteById(
                            autoInstantiationConfigurationId,
                            testUserId
                    );
                    handle.attach(JdbiEventConfiguration.class).updateMaxOccurrencesPerUserById(
                            autoInstantiationConfigurationId,
                            1
                    );
                    JdbiEventConfigurationOccurrenceCounter jdbiEventConfCounter = handle.attach(
                            JdbiEventConfigurationOccurrenceCounter.class
                    );
                    jdbiEventConfCounter.getOrCreateNumOccurrences(autoInstantiationConfigurationId, testUserId);
                    jdbiEventConfCounter.incNumOccurrences(autoInstantiationConfigurationId, testUserId);
                }
        );
        int countBefore = getNumActivityInstances();
        HttpResponse response = createTestPatchAnswerPayloadAndExecuteRequest();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        int countAfter = getNumActivityInstances();
        Assert.assertTrue(countAfter == countBefore);
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteById(
                            autoInstantiationConfigurationId,
                            testUserId
                    );
                }
        );
    }

    private int getNumActivityInstances() {
        return TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivityInstance.class).getCount());
    }

    private PatchAnswerPayload createTestPatchAnswerPayload() {
        PatchAnswerPayload payload = new PatchAnswerPayload();
        payload.addSubmission(new AnswerSubmission(TestData.boolStableId, null, new Gson().toJsonTree(true)));
        return payload;
    }

    private HttpResponse createTestPatchAnswerPayloadAndExecuteRequest() throws IOException {
        PatchAnswerPayload data = createTestPatchAnswerPayload();
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, new Gson().toJson(data));
        HttpResponse response = request.execute().returnResponse();
        return response;
    }

    private static final class TestData {
        static String sourceActivityCode;
        static String targetActivityCode;
        static String boolStableId;
        static String activityInstanceGuid;
        static long activityInstanceId;
        static String expressionGuid;
    }
}
