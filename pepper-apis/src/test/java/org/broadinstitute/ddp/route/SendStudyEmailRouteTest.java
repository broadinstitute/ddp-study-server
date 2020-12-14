package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.json.studyemail.Attachment;
import org.broadinstitute.ddp.json.studyemail.SendStudyEmailPayload;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.fileupload.FileUploadStatus;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SendStudyEmailRouteTest extends IntegrationTestSuite.TestCase {

    static TestDataSetupUtil.GeneratedTestData testData;
    static String url;
    static String templateKey;
    static String attachmentGuid;
    static Long insertedEventConfigId;
    static Long insertedFileUploadId;

    @BeforeClass
    public static void setUp() {
        templateKey = ConfigUtil.getTestingSendgridTemplates(RouteTestUtil.getConfig()).getConfig(
                "dataAccessRequest").getString(ConfigFile.Sendgrid.TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            insertedEventConfigId = setUpEmailConfiguration(handle, templateKey);
            attachmentGuid = setUpAttachment(handle);
        });
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.SEND_STUDY_EMAIL;
        url = url.replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}");
    }

    @AfterClass
    public static void tearDown() {
        TransactionWrapper.useTxn(handle -> {
            if (insertedEventConfigId != null) {
                int numRows = handle.attach(QueuedEventDao.class)
                        .deleteQueuedEventsByEventConfigurationId(insertedEventConfigId);
                if (numRows != 1) {
                    Assert.fail("Deleted " + numRows + " queued events for configuration id " + insertedEventConfigId);
                }
            }
        });
    }

    @Test
    public void testNotification() {
        int numStartingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        Attachment attachment = new Attachment("file-name.pdf", attachmentGuid);
        Map<String, String> subst = new HashMap<>();
        subst.put("org_dept", "HR");
        subst.put("request_date", "2020-12-11");
        subst.put("researcher_name", "John");
        SendStudyEmailPayload payload = new SendStudyEmailPayload(subst, List.of(attachment));
        given()
                .pathParam("studyGuid", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(url).then().assertThat()
                .statusCode(202).contentType(ContentType.JSON)
                .and().extract().response();

        TransactionWrapper.useTxn(handle -> {
            EventDao eventDao = handle.attach(EventDao.class);
            List<QueuedEventDto> pendingEvents = eventDao.findPublishableQueuedEvents();
            int numQueuedEvents = 0;
            for (QueuedEventDto pendingEvent : pendingEvents) {
                if (pendingEvent.getEventConfigurationId() == insertedEventConfigId) {
                    QueuedNotificationDto notificationDto = (QueuedNotificationDto) pendingEvent;

                    var templates = eventDao.getNotificationTemplatesForEvent(notificationDto.getEventConfigurationId());
                    Assert.assertEquals(1, templates.size());
                    Assert.assertEquals(templateKey, templates.get(0).getTemplateKey());

                    List<Long> fileUploadIds = handle.attach(QueuedEventDao.class).getJdbiQueuedNotificationAttachment()
                            .getFileUploadIdsByQueuedId(pendingEvent.getQueuedEventId());

                    Assert.assertNotNull(fileUploadIds);
                    Assert.assertEquals(1, fileUploadIds.size());
                    Assert.assertEquals(insertedFileUploadId, fileUploadIds.get(0));
                    numQueuedEvents++;
                }
            }
            Assert.assertEquals("Failed to queue proper number of email events", 1, numQueuedEvents);
        });

        int numPendingQueuedEvents = getNumPendingEventsInSeparateTransaction();
        Assert.assertEquals(numStartingQueuedEvents + 1, numPendingQueuedEvents);
    }

    private int getNumPendingEventsInSeparateTransaction() {
        return TransactionWrapper.withTxn(handle -> handle.attach(EventDao.class).findPublishableQueuedEvents().size());
    }

    private static Long setUpEmailConfiguration(Handle handle, String key) {
        JdbiExpression expressionDao = handle.attach(JdbiExpression.class);
        long trueExpressionId = expressionDao.insertExpression("true").getId();
        long falseExpressionId = expressionDao.insertExpression("false").getId();

        SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(key, "en", false);
        long emailActionId = handle.attach(EventActionDao.class).insertNotificationAction(eventAction);

        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        long eventTriggerId = eventTriggerDao.insertStaticTrigger(EventTriggerType.SEND_STUDY_EMAIL);

        return handle.attach(JdbiEventConfiguration.class).insert(eventTriggerId, emailActionId, testData.getStudyId(),
                Instant.now().toEpochMilli(), null, null, trueExpressionId,
                falseExpressionId, true, 1);
    }

    private static String setUpAttachment(Handle handle) {
        String fileUploadGuid = GuidUtils.randomStandardGuid();
        insertedFileUploadId = handle.attach(FileUploadDao.class).insertFileUpload(fileUploadGuid, "bucketFilename",
                Instant.now(), "file-name.pdf", 100L, FileUploadStatus.AUTHORIZED);
        return fileUploadGuid;
    }

}
