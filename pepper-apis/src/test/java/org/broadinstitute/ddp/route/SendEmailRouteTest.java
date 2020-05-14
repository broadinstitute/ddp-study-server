package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FROM_EMAIL;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.json.SendEmailPayload;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendEmailRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(SendEmailRouteTest.class);

    static TestDataSetupUtil.GeneratedTestData testData;
    static String url;
    static Long insertedKnownUserEventConfigId;
    static Long insertedUnknownUserEventConfigId;
    static ActivityInstanceDto createdActivityInstanceDto;
    // set this to true to leave the event queued if you want to run housekeeping locally and see
    // the email get sent
    static boolean skipCleanup = false;
    private static String resendEmailTemplateKey;
    private static String userNotInStudyTemplateKey;

    @BeforeClass
    public static void setUp() throws Exception {
        resendEmailTemplateKey = ConfigUtil.getTestingSendgridTemplates(RouteTestUtil.getConfig()).getConfig(
                "currentActivity").getString(ConfigFile.Sendgrid.TEMPLATE);
        userNotInStudyTemplateKey = ConfigUtil.getTestingSendgridTemplates(RouteTestUtil.getConfig()).getConfig(
                "userNotEnrolledInStudy").getString(ConfigFile.Sendgrid.TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            setupEmailConfigurations(handle);
        });
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.SEND_EMAIL;
        url = url.replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}");

    }

    @AfterClass
    public static void tearDown() {
        if (!skipCleanup) {
            TransactionWrapper.useTxn(handle -> {
                if (insertedKnownUserEventConfigId != null) {
                    int numRows = handle.attach(QueuedEventDao.class)
                            .deleteQueuedEventsByEventConfigurationId(insertedKnownUserEventConfigId);
                    if (numRows != 1) {
                        Assert.fail("Deleted " + numRows + " queued events for configuration id " + insertedKnownUserEventConfigId);
                    }
                }
                if (insertedUnknownUserEventConfigId != null) {
                    int numRows = handle.attach(QueuedEventDao.class)
                            .deleteQueuedEventsByEventConfigurationId(insertedUnknownUserEventConfigId);
                    if (numRows != 1) {
                        Assert.fail("Deleted " + numRows + " queued events for configuration id " + insertedUnknownUserEventConfigId);
                    }
                }

            });
        } else {
            LOG.warn("Skipping removal of event configuration so that housekeeping will send it.  This may impact "
                    + "other tests.");
        }
    }


    private Response postResendAndAssert200(SendEmailPayload sendEmailPayload) {
        return given()
                .pathParam("studyGuid", testData.getStudyGuid())
                .body(sendEmailPayload, ObjectMapperType.GSON)
                .when().post(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();
    }

    @Test
    public void testEmailResendToKnownUser() {
        int numStartingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        SendEmailPayload payload = new SendEmailPayload(testData.getTestingUser().getEmail());
        postResendAndAssert200(payload);

        TransactionWrapper.useTxn(handle -> {
            List<QueuedEventDto> pendingEvents = handle.attach(EventDao.class).findPublishableQueuedEvents();
            int numQueuedEvents = 0;
            for (QueuedEventDto pendingEvent : pendingEvents) {
                if (pendingEvent.getEventConfigurationId() == insertedKnownUserEventConfigId) {
                    QueuedNotificationDto notificationDto = (QueuedNotificationDto) pendingEvent;

                    Assert.assertEquals(notificationDto.getTemplateKey(), resendEmailTemplateKey);
                    Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = notificationDto
                            .getTemplateSubstitutions();

                    boolean foundInstanceGuid = false;
                    for (NotificationTemplateSubstitutionDto templateSubstitution : templateSubstitutions) {
                        if (templateSubstitution.getVariableName().equals(DDP_ACTIVITY_INSTANCE_GUID)) {
                            Assert.assertEquals(createdActivityInstanceDto.getGuid(),
                                    templateSubstitution.getValue());
                            foundInstanceGuid = true;
                        }
                    }
                    Assert.assertTrue("Did not find instance guid in template substitutions ", foundInstanceGuid);
                    numQueuedEvents++;
                }
            }
            Assert.assertEquals("Failed to queue proper number of email events", 1, numQueuedEvents);
        });

        int numEndingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        Assert.assertEquals(numStartingQueuedEvents + 1, numEndingQueuedEvents);
    }

    @Test
    public void testEmailResendToUnknownUser() {
        String dummyEmail = "aVeryValidEmailAddress@datadonationplatform.org";

        int numStartingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        SendEmailPayload payload = new SendEmailPayload(dummyEmail);
        postResendAndAssert200(payload);

        TransactionWrapper.useTxn(handle -> {
            List<QueuedEventDto> pendingEvents = handle.attach(EventDao.class).findPublishableQueuedEvents();
            int numQueuedEvents = 0;
            for (QueuedEventDto pendingEvent : pendingEvents) {
                if (pendingEvent.getEventConfigurationId() == insertedUnknownUserEventConfigId) {
                    QueuedNotificationDto notificationDto = (QueuedNotificationDto) pendingEvent;

                    Assert.assertEquals(notificationDto.getTemplateKey(), userNotInStudyTemplateKey);

                    Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = notificationDto.getTemplateSubstitutions();

                    boolean foundEmail = false;
                    for (NotificationTemplateSubstitutionDto templateSubstitution : templateSubstitutions) {
                        if (templateSubstitution.getVariableName().equals(DDP_PARTICIPANT_FROM_EMAIL)) {
                            foundEmail = dummyEmail.equals(templateSubstitution.getValue());
                        }
                    }
                    Assert.assertTrue("Did not find email in template substitutions", foundEmail);

                    numQueuedEvents++;
                }
            }
            Assert.assertEquals("Failed to queue proper number of email events", 1, numQueuedEvents);
        });
        int numEndingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        Assert.assertEquals(numStartingQueuedEvents + 1, numEndingQueuedEvents);
    }

    private int getNumPendingEventsInSeparateTransaction() {
        return TransactionWrapper.withTxn(handle -> handle.attach(EventDao.class).findPublishableQueuedEvents().size());
    }

    private static WorkflowState setUpWorkflow(Handle handle, long activityId) {
        WorkflowDao workflowDao = handle.attach(WorkflowDao.class);

        WorkflowState workflowState = new ActivityState(activityId);
        WorkflowState returnUserState = StaticState.returningUser();

        String expr = "true";
        WorkflowTransition returningTransition = new WorkflowTransition(testData.getStudyId(), returnUserState, workflowState, expr,
                1);
        workflowDao.insertTransitions(Collections.singletonList(returningTransition));

        long workflowStateId = workflowDao.findWorkflowStateId(new ActivityState(activityId)).get();

        LOG.info("Setup activity {} and workflow state {} for testing email resend for known user", activityId, workflowStateId);
        return workflowState;
    }

    private static long setUpEmailConfiguration(Handle handle, String key, WorkflowState workflowState) throws Exception {
        JdbiExpression expressionDao = handle.attach(JdbiExpression.class);
        long trueExpressionId = expressionDao.insertExpression("true").getId();
        long falseExpressionId = expressionDao.insertExpression("false").getId();

        SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(key, "en");
        long emailActionId = handle.attach(EventActionDao.class).insertNotificationAction(eventAction);

        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        long eventTriggerId;
        if (key.equals(resendEmailTemplateKey) && workflowState != null) {
            eventTriggerId = eventTriggerDao
                    .insertWorkflowTrigger(handle.attach(WorkflowDao.class)
                            .findWorkflowStateId(workflowState).get());
        } else if (key.equals(userNotInStudyTemplateKey)) {
            eventTriggerId = eventTriggerDao.insertUserNotInStudyTrigger();
        } else {
            throw new Exception("Unsupported key type");
        }

        return handle.attach(JdbiEventConfiguration.class).insert(eventTriggerId, emailActionId, testData.getStudyId(),
                Instant.now().toEpochMilli(), null, null, trueExpressionId,
                falseExpressionId, true, 1);
    }

    private static void setupEmailConfigurations(Handle handle) throws Exception {
        String activityCode = "ResendEmail" + System.currentTimeMillis();
        FormActivityDef activity = TestDataSetupUtil.createBlankActivity(handle,
                activityCode,
                testData.getUserGuid(),
                testData.getStudyGuid());
        long activityId = activity.getActivityId();

        // create activity instance for test user
        createdActivityInstanceDto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(),
                testData.getUserGuid());

        LOG.info("Created activity instance {} for testing email resend for user {}", createdActivityInstanceDto
                .getGuid(), testData.getStudyGuid());


        WorkflowState workflowState = setUpWorkflow(handle, activityId);

        insertedKnownUserEventConfigId = setUpEmailConfiguration(handle, resendEmailTemplateKey, workflowState);
        insertedUnknownUserEventConfigId = setUpEmailConfiguration(handle, userNotInStudyTemplateKey, null);
    }





}
