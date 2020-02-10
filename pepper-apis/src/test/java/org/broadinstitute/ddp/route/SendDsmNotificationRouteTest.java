package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.List;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiDsmNotificationEventType;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.json.dsm.DsmNotificationEvent;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDsmNotificationRouteTest extends DsmRouteTest {

    public static final String SENDGRID_TEST_TEMPLATE = "e14a7315-fc82-4c66-bea9-5071d68aee2f";
    private static final Logger LOG = LoggerFactory.getLogger(SendDsmNotificationRouteTest.class);
    private static TestDataSetupUtil.GeneratedTestData generatedTestData;
    private static String url;
    private static long insertedEventConfigId = -1;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";

    @BeforeClass
    public static void initClassVars() {
        url = RouteTestUtil.getTestingBaseUrl() + API.DSM_NOTIFICATION;
    }

    private static DsmNotificationEvent makeDsmNotificationEvent(String eventType) {
        return new DsmNotificationEvent(null, eventType, Instant.now().toEpochMilli() / 1000L);
    }

    @Before
    public void insertTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    insertedEventConfigId = TestDataSetupUtil.generateDsmNotificationTestEventConfiguration(
                            handle,
                            generatedTestData,
                            SENDGRID_TEST_TEMPLATE);
                    assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                            .bind("legacyAltPid", legacyAltPid)
                            .bind("guid", userGuid)
                            .execute());


                    TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED);
                }
        );
    }

    @After
    public void deleteTestData() {
        TransactionWrapper.useTxn(handle -> {
            QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
            queuedEventDao.deleteQueuedEventsByEventConfigurationId(insertedEventConfigId);
            TestDataSetupUtil.deleteEnrollmentStatus(handle, generatedTestData);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    /*
     * Verifies that POSTing to the DSM notification endpoint leads to creation
     * of queued event(s) that will eventually end up as user notifications
     * after being processed by Housekeeping
     */
    @Test
    public void testCreateDsmNotificationWithLegacyAltPid_Success() throws Exception {
        int numStartingQueuedEvents = TransactionWrapper.withTxn(handle ->
                handle.attach(EventDao.class).findPublishableQueuedEvents().size());

        LOG.info("testCreateDsmNotification_Success");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent(JdbiDsmNotificationEventType.SALIVA_RECEIVED);
        HttpResponse response = sendRequestAndReturnResponse(generatedTestData.getStudyGuid(), legacyAltPid, dsmEvent);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        // verify that notification was queued
        checkifNotificationQueued(numStartingQueuedEvents);
    }

    private void checkifNotificationQueued(int numStartingQueuedEvents) {
        TransactionWrapper.useTxn(handle -> {
            List<QueuedEventDto> pendingEvents = handle.attach(EventDao.class).findPublishableQueuedEvents();
            boolean foundQueuedTemplate = false;
            for (QueuedEventDto pendingEvent : pendingEvents) {
                if (pendingEvent instanceof QueuedNotificationDto) {
                    QueuedNotificationDto notificationDto = (QueuedNotificationDto) pendingEvent;
                    if (SENDGRID_TEST_TEMPLATE.equals(notificationDto.getTemplateKey())) {
                        foundQueuedTemplate = true;
                    }
                }
            }

            Assert.assertTrue(foundQueuedTemplate);

            Assert.assertEquals("Notification does not appear to have been queued.", numStartingQueuedEvents + 1,
                    pendingEvents.size());
        });
    }

    /*
     * Verifies that POSTing to the DSM notification endpoint leads to creation
     * of queued event(s) that will eventually end up as user notifications
     * after being processed by Housekeeping
     */
    @Test
    public void testCreateDsmNotification_Success() throws Exception {
        int numStartingQueuedEvents = TransactionWrapper.withTxn(handle -> {
            return handle.attach(EventDao.class).findPublishableQueuedEvents().size();
        });

        LOG.info("testCreateDsmNotification_Success");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent(JdbiDsmNotificationEventType.SALIVA_RECEIVED);
        HttpResponse response = sendRequestAndReturnResponse(generatedTestData.getStudyGuid(), generatedTestData.getUserGuid(), dsmEvent);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        // verify that notification was queued
        checkifNotificationQueued(numStartingQueuedEvents);
    }

    @Test
    public void testCreateDsmNotification_NoStudySpecified_Got404() throws Exception {
        LOG.info("testCreateDsmNotification_NoStudySpecified_Got404");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent(JdbiDsmNotificationEventType.SALIVA_RECEIVED);
        HttpResponse response = sendRequestAndReturnResponse("BADDCAFE00", generatedTestData.getUserGuid(), dsmEvent);
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testCreateDsmNotification_NoUserSpecified_Got404() throws Exception {
        LOG.info("testCreateDsmNotification_NoUserSpeficied_Got404");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent(JdbiDsmNotificationEventType.SALIVA_RECEIVED);
        HttpResponse response = sendRequestAndReturnResponse(generatedTestData.getStudyGuid(), "BADDCAFE00", dsmEvent);
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testCreateDsmNotification_NoEventTypeSpecified_Got404() throws Exception {
        LOG.info("testCreateDsmNotification_NoEventTypeSpecified_Got404");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent("SOMETHING_STRANGE_RECEIVED");
        HttpResponse response = sendRequestAndReturnResponse(generatedTestData.getStudyGuid(), generatedTestData.getUserGuid(), dsmEvent);
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testCreateDsmNotification_NoEventConfigSpecified_ButSuccess() throws Exception {
        LOG.info("testCreateDsmNotification_NoEventConfigSpecified_ButSuccess");
        DsmNotificationEvent dsmEvent = makeDsmNotificationEvent(JdbiDsmNotificationEventType.SALIVA_RECEIVED);
        HttpResponse response = sendRequestAndReturnResponse(generatedTestData.getStudyGuid(), generatedTestData.getUserGuid(), dsmEvent);
        deleteTestData();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
    }

    private HttpResponse sendRequestAndReturnResponse(String studyGuid, String userGuid, DsmNotificationEvent payload) throws Exception {
        String renderedUrl = url.replace(PathParam.STUDY_GUID, studyGuid)
                .replace(PathParam.USER_GUID, userGuid);
        LOG.info("Sending the POST request to {}", renderedUrl);
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                dsmClientAccessToken,
                renderedUrl,
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        return response;
    }

}
