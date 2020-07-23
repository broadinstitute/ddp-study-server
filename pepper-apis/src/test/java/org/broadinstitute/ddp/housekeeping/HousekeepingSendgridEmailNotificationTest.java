package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.COMPLETE;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.Housekeeping;
import org.broadinstitute.ddp.HousekeepingTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatusType;
import org.broadinstitute.ddp.db.dao.JdbiActivityStatusTrigger;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.EventTriggerSql;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessage;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HousekeepingSendgridEmailNotificationTest extends HousekeepingTest {

    public static final int MESSAGE_HANDER_TIMEOUT_SECONDS = 5;
    private static final Logger LOG = LoggerFactory.getLogger(HousekeepingSendgridEmailNotificationTest.class);
    public static String template;
    public static String templateVersion;
    private static long insertedEventConfigId = -1;
    private static Auth0Util.TestingUser testingUser;
    private static FormActivityDef testActivity;
    private static ActivityInstanceDto testActivityInstance;


    @BeforeClass
    public static void setupTestData() {
        Config emailTestingTemplate = ConfigUtil.getGenericSendgridTestingTemplate(cfg);
        template = emailTestingTemplate.getString(ConfigFile.Sendgrid.TEMPLATE);
        templateVersion = emailTestingTemplate.getString(ConfigFile.Sendgrid.TEMPLATE_VERSION);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
            EventActionDao eventActionDao = apisHandle.attach(EventActionDao.class);
            JdbiExpression expressionDao = apisHandle.attach(JdbiExpression.class);
            EventTriggerSql eventTriggerDao = apisHandle.attach(EventTriggerSql.class);
            JdbiActivityStatusTrigger activityStatusTriggerDao = apisHandle.attach(JdbiActivityStatusTrigger.class);
            ActivityInstanceDao activityInstanceDao = apisHandle.attach(ActivityInstanceDao.class);
            JdbiActivityInstanceStatusType statusTypeDao = apisHandle.attach(JdbiActivityInstanceStatusType.class);
            JdbiEventConfiguration eventConfigDao = apisHandle.attach(JdbiEventConfiguration.class);

            SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(template, "en", false);
            long testEmailActionId = eventActionDao.insertNotificationAction(eventAction);
            TestDataSetupUtil.GeneratedTestData generatedTestData = TestDataSetupUtil.generateBasicUserTestData(apisHandle);

            // create a new activity
            String activityCode = "HOUSEKEEPING_TEST_ACTIVITY" + System.currentTimeMillis();
            testActivity = TestDataSetupUtil.createBlankActivity(apisHandle, activityCode, generatedTestData
                    .getUserGuid(), generatedTestData.getStudyGuid());

            // create a new user
            testingUser = generatedTestData.getTestingUser();
            long testingUserId = testingUser.getUserId();
            String testingUserGuid = testingUser.getUserGuid();
            LOG.info("Using generated testing user {} with guid {} and auth0 id {}", testingUserId, testingUserGuid,
                    testingUser.getAuth0Id());

            // create an activity instance of the created activity for the user
            testActivityInstance = activityInstanceDao.insertInstance(testActivity.getActivityId(),
                    testingUserGuid, testingUserGuid, CREATED, false);
            LOG.info("Created activity instance {} with guid {}", testActivityInstance.getId(),
                    testActivityInstance.getGuid());

            // create pex precondition and cancel conditions
            long trueExpressionId = expressionDao.insertExpression("true").getId();
            long falseExpressionId = expressionDao.insertExpression("false").getId();

            // setup a status change event trigger so that when status changes, an event is queued
            long eventTriggerId = eventTriggerDao.insertBaseTrigger(EventTriggerType.ACTIVITY_STATUS);
            activityStatusTriggerDao.insert(eventTriggerId, testActivity.getActivityId(), COMPLETE);

            insertedEventConfigId = eventConfigDao.insert(eventTriggerId, testEmailActionId, generatedTestData
                            .getStudyId(), Instant.now().toEpochMilli(), 1, null, trueExpressionId,
                    falseExpressionId, true, 1);
            assertThat(testEmailActionId, notNullValue());

        });
    }

    @AfterClass
    public static void afterClass() {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
            QueuedEventDao queuedEventDao = apisHandle.attach(QueuedEventDao.class);
            int numRowsDeleted = queuedEventDao.deleteQueuedEventsByEventConfigurationId(insertedEventConfigId);

            if (numRowsDeleted != 1) {
                LOG.warn("Deleted " + numRowsDeleted + " queued events for event configuration "
                        + insertedEventConfigId);
            }
        });
    }

    /**
     * Inserts a queued event for sending an email and verifies
     * it has been sent indirectly by watching the housekeeping
     * logging
     */
    @Test
    public void testEmailSendEvent() throws Exception {
        // need to block waiting for post-handling since it's happening in another thread
        final Semaphore available = new Semaphore(1);
        available.acquire();
        final AtomicInteger numEventsHandled = new AtomicInteger(0);
        final AtomicBoolean wasMessageProcessed = new AtomicBoolean(false);
        final AtomicBoolean wasEventIgnored = new AtomicBoolean(false);
        Housekeeping.AfterHandlerCallback afterHandler = new Housekeeping.AfterHandlerCallback() {
            @Override
            public void messageHandled(HousekeepingMessage message, String eventConfigurationId) {
                numEventsHandled.incrementAndGet();
                if (Long.parseLong(eventConfigurationId) == insertedEventConfigId) {
                    wasMessageProcessed.set(true);
                    available.release();
                }
            }

            @Override
            public void eventIgnored(String eventConfigurationId) {
                if (Long.parseLong(eventConfigurationId) == insertedEventConfigId) {
                    wasEventIgnored.set(true);
                }
            }
        };
        Housekeeping.setAfterHandler(afterHandler);

        String emailSentLogEntry = String.format("Sent template %s version %s to %s", template,
                templateVersion, testingUser.getEmail());
        moveStatusToInProgressAndThenToComplete();
        LOG.info("Inserted status that should trigger email event processing for activity instance {}",
                testActivityInstance.getGuid());
        boolean wasLogEntryFound = waitForLogging(emailSentLogEntry);

        // todo arz find a way make this pattern of block-for-callback easier to use
        available.tryAcquire(MESSAGE_HANDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Post-handling callback was not called.  Email sending event does not appear to have been "
                + "processed.", wasMessageProcessed.get());
        assertTrue("No evidence that email was sent", wasLogEntryFound);
        assertEquals("Handled " + numEventsHandled + " events", 1, numEventsHandled.get());
        assertFalse(wasEventIgnored.get());
    }

    /**
     * Forces a new status change for the activity instance by
     * first setting the status to in progress and then changing it
     * to completed
     */
    private void moveStatusToInProgressAndThenToComplete() {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            ActivityInstanceStatusDao statusDao = handle.attach(ActivityInstanceStatusDao.class);
            statusDao.insertStatus(testActivityInstance.getId(),
                    InstanceStatusType.IN_PROGRESS,
                    Instant.now().toEpochMilli(),
                    testingUser.getUserGuid());
            statusDao.insertStatus(testActivityInstance.getId(),
                    InstanceStatusType.COMPLETE,
                    Instant.now().toEpochMilli() + 1,
                    testingUser.getUserGuid());
        });
    }

    @After
    public void after() {
        Housekeeping.clearAfterHandler();
    }
}
