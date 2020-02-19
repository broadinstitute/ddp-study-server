package org.broadinstitute.ddp.housekeeping.handler;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_SALUTATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sendgrid.Email;
import com.sendgrid.Mail;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class EmailNotificationHandlerTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private SendGridClient mockSendGrid;
    private PdfService mockPdf;
    private PdfBucketService mockPdfBucket;
    private PdfGenerationService mockPdfGen;
    private EmailNotificationHandler handler;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Before
    public void init() {
        mockSendGrid = mock(SendGridClient.class);
        mockPdf = mock(PdfService.class);
        mockPdfBucket = mock(PdfBucketService.class);
        mockPdfGen = mock(PdfGenerationService.class);
        handler = new EmailNotificationHandler(mockSendGrid, mockPdf, mockPdfBucket, mockPdfGen);
    }

    @Test
    public void testGenerateSalutation() {
        assertEquals("default", EmailNotificationHandler.generateSalutation(null, null, "default"));
        assertEquals("default", EmailNotificationHandler.generateSalutation("", "", "default"));
        assertEquals("default", EmailNotificationHandler.generateSalutation(null, "last", "default"));
        assertEquals("default", EmailNotificationHandler.generateSalutation("", "last", "default"));
        assertEquals("default", EmailNotificationHandler.generateSalutation("first", null, "default"));
        assertEquals("default", EmailNotificationHandler.generateSalutation("first", "", "default"));
        assertEquals("Dear first last,", EmailNotificationHandler.generateSalutation("first", "last", "default"));
    }

    @Test
    public void testMessageShouldBeIgnored_byUserStatus() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

            jdbiEnrollment.changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(),
                    EnrollmentStatusType.REGISTERED);
            assertFalse(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            jdbiEnrollment.changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED);
            assertFalse(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            jdbiEnrollment.changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(),
                    EnrollmentStatusType.CONSENT_SUSPENDED);
            assertFalse(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            jdbiEnrollment.changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(),
                    EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT);
            assertTrue(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            jdbiEnrollment.changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(),
                    EnrollmentStatusType.EXITED_AFTER_ENROLLMENT);
            assertTrue(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            handle.rollback();
        });
    }

    @Test
    public void testMessageShouldBeIgnored_byDoNotContactPreference() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.ENROLLED);
            JdbiProfile jdbiProfile = handle.attach(JdbiProfile.class);

            // no preference
            jdbiProfile.updateDoNotContact(null, testData.getUserId());
            assertFalse(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            // opted in
            jdbiProfile.updateDoNotContact(false, testData.getUserId());
            assertFalse(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            // opted out
            jdbiProfile.updateDoNotContact(true, testData.getUserId());
            assertTrue(handler.messageShouldBeIgnored(handle, testData.getStudyGuid(), testData.getUserGuid()));

            handle.rollback();
        });
    }

    @Test
    public void testBuildSubstitutions() {
        var msg = new NotificationMessage(
                NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                "template", List.of("to@ddp.org"), "first", "last", "guid",
                "study", "pepper", "from@ddp.org", "key", "salutation",
                List.of(new NotificationTemplateSubstitutionDto("foo", "bar"),
                        new NotificationTemplateSubstitutionDto("alice", "bob")),
                "url", 1L);
        var subs = handler.buildSubstitutions(msg);
        assertEquals("Dear first last,", subs.get(DDP_SALUTATION));
        assertEquals("url", subs.get(DDP_BASE_WEB_URL));
        assertEquals("guid", subs.get(DDP_PARTICIPANT_GUID));
        assertEquals("first", subs.get(DDP_PARTICIPANT_FIRST_NAME));
        assertEquals("last", subs.get(DDP_PARTICIPANT_LAST_NAME));
        assertEquals("bar", subs.get("foo"));
        assertEquals("bob", subs.get("alice"));
    }

    @Test
    public void testBuildAttachments_generatePdf() throws IOException {
        // Setup expected data
        var pdfConfig = new PdfConfiguration(
                new PdfConfigInfo(1L, "config-name", "filename", "display-name"),
                new PdfVersion("v1", 1L));
        String content = "pdf from test!";

        // Wires up mocks
        var mockHandle = mock(Handle.class);
        var mockEventDao = mock(EventDao.class);
        when(mockHandle.attach(EventDao.class)).thenReturn(mockEventDao);
        when(mockHandle.attach(JdbiUmbrellaStudy.class)).thenReturn(mock(JdbiUmbrellaStudy.class));
        when(mockEventDao.getPdfAttachmentsForEvent(1L)).thenReturn(List.of(new PdfAttachment(1L, true)));
        when(mockPdf.findFullConfigForUser(any(), eq(1L), any(), any())).thenReturn(pdfConfig);
        when(mockPdfBucket.getPdfFromBucket(any())).thenReturn(Optional.empty());
        when(mockPdfBucket.getBucketName()).thenReturn("test-bucket");
        when(mockPdfGen.generateFlattenedPdfForConfiguration(any(), any(), any())).thenReturn(content.getBytes());

        // Run test and assertions
        var actual = handler.buildAttachments(mockHandle, testData.getStudyGuid(), "guid", 1L);
        assertNotNull(actual);
        assertEquals(1, actual.size());

        var actualAttachment = actual.get(0);
        assertNotNull(actualAttachment);
        assertTrue(actualAttachment.getType().contains("pdf"));
        assertEquals("filename.pdf", actualAttachment.getFilename());

        var actualContent = new String(Base64.getDecoder().decode(actualAttachment.getContent()));
        assertEquals(content, actualContent);
    }

    @Test
    public void testBuildAttachments_fromBucket() {
        // Setup expected data
        var pdfConfig = new PdfConfiguration(
                new PdfConfigInfo(1L, "config-name", "filename", "display-name"),
                new PdfVersion("v1", 1L));
        String content = "pdf from test!";
        var input = new ByteArrayInputStream(content.getBytes());

        // Wires up mocks
        var mockHandle = mock(Handle.class);
        var mockEventDao = mock(EventDao.class);
        when(mockHandle.attach(EventDao.class)).thenReturn(mockEventDao);
        when(mockHandle.attach(JdbiUmbrellaStudy.class)).thenReturn(mock(JdbiUmbrellaStudy.class));
        when(mockEventDao.getPdfAttachmentsForEvent(1L)).thenReturn(List.of(new PdfAttachment(1L, false)));
        when(mockPdf.findFullConfigForUser(any(), eq(1L), any(), any())).thenReturn(pdfConfig);
        when(mockPdfBucket.getPdfFromBucket(any())).thenReturn(Optional.of(input));
        when(mockPdfBucket.getBucketName()).thenReturn("test-bucket");

        // Run test and assertions
        var actual = handler.buildAttachments(mockHandle, testData.getStudyGuid(), "guid", 1L);
        assertNotNull(actual);
        assertEquals(1, actual.size());

        var actualAttachment = actual.get(0);
        assertNotNull(actualAttachment);
        assertTrue(actualAttachment.getType().contains("pdf"));
        assertEquals("filename.pdf", actualAttachment.getFilename());

        var actualContent = new String(Base64.getDecoder().decode(actualAttachment.getContent()));
        assertEquals(content, actualContent);
    }

    @Test
    public void testBuildAttachments_noEmailAttachments() {
        var mockHandle = mock(Handle.class);
        var mockEventDao = mock(EventDao.class);
        when(mockHandle.attach(EventDao.class)).thenReturn(mockEventDao);
        when(mockHandle.attach(JdbiUmbrellaStudy.class)).thenReturn(mock(JdbiUmbrellaStudy.class));
        when(mockEventDao.getPdfAttachmentsForEvent(1L)).thenReturn(Collections.emptyList());

        var actual = handler.buildAttachments(mockHandle, "study", "guid", 1L);
        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testBuildAttachments_error() {
        // Setup expected data
        var pdfConfig = new PdfConfiguration(
                new PdfConfigInfo(1L, "config-name", "filename", "display-name"),
                new PdfVersion("v1", 1L));

        // Wires up mocks
        var mockHandle = mock(Handle.class);
        var mockEventDao = mock(EventDao.class);
        when(mockHandle.attach(EventDao.class)).thenReturn(mockEventDao);
        when(mockHandle.attach(JdbiUmbrellaStudy.class)).thenReturn(mock(JdbiUmbrellaStudy.class));
        when(mockEventDao.getPdfAttachmentsForEvent(1L)).thenReturn(List.of(new PdfAttachment(1L, true)));
        when(mockPdf.findFullConfigForUser(any(), eq(1L), any(), any())).thenReturn(pdfConfig);
        when(mockPdfBucket.getPdfFromBucket(any())).thenThrow(new DDPException("testing"));
        when(mockPdfBucket.getBucketName()).thenReturn("test-bucket");

        try {
            handler.buildAttachments(mockHandle, testData.getStudyGuid(), "guid", 1L);
            fail("expected exception not thrown");
        } catch (MessageHandlingException e) {
            assertTrue(e.getMessage().contains("test-bucket"));
            assertTrue(e.shouldRetry());
        }
    }

    @Test
    public void testHandleMessage() {
        String pdfName = "test.pdf";
        String pdfContent = "from test!";
        InputStream input = new ByteArrayInputStream(pdfContent.getBytes());

        // Setup mocks
        var spiedHandler = spy(handler);
        doReturn(false).when(spiedHandler).messageShouldBeIgnored(any(), any(), any());
        doReturn(List.of(SendGridClient.newPdfAttachment(pdfName, input)))
                .when(spiedHandler).buildAttachments(any(), any(), any(), anyLong());
        doReturn(Map.of("foo", "bar")).when(spiedHandler).buildSubstitutions(any());
        when(mockSendGrid.getTemplateActiveVersionId(any())).thenReturn(ApiResult.ok(200, "v1"));
        when(mockSendGrid.sendMail(any())).thenReturn(ApiResult.ok(202, null));

        // Run test and capture
        var msg = new NotificationMessage(
                NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                "template1", List.of("to@ddp.org"), "first", "last", "guid",
                "study", "pepper", "from@ddp.org", "key", "salutation", List.of(), "url", 1L);
        spiedHandler.handleMessage(msg);

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mockSendGrid, times(1)).sendMail(captor.capture());

        // Assert the mail package itself
        var actualMail = captor.getValue();
        assertEquals("template1", actualMail.getTemplateId());
        assertEquals(new Email("from@ddp.org", "pepper"), actualMail.getFrom());
        assertEquals(1, actualMail.getPersonalization().size());
        assertEquals(1, actualMail.getAttachments().size());

        // Assert attachments
        var att = actualMail.getAttachments().get(0);
        assertTrue(att.getType().contains("pdf"));
        assertEquals(pdfName, att.getFilename());
        assertEquals(pdfContent, new String(Base64.getDecoder().decode(att.getContent())));

        // Assert customizations
        var ps = actualMail.getPersonalization().get(0);
        assertEquals(1, ps.getTos().size());
        assertEquals(new Email("to@ddp.org", "to@ddp.org"), ps.getTos().get(0));
        assertEquals(1, ps.getSubstitutions().size());
        assertEquals("bar", ps.getSubstitutions().get("foo"));
    }

    @Test
    public void testHandleMessage_forNonParticipants() {
        // Setup mocks
        var spiedHandler = spy(handler);
        doReturn(false).when(spiedHandler).messageShouldBeIgnored(any(), any(), any());
        doReturn(Collections.emptyList()).when(spiedHandler).buildAttachments(any(), any(), any(), anyLong());
        doReturn(Collections.emptyMap()).when(spiedHandler).buildSubstitutions(any());
        when(mockSendGrid.getTemplateActiveVersionId(any())).thenReturn(ApiResult.ok(200, "v1"));
        when(mockSendGrid.sendMail(any())).thenReturn(ApiResult.ok(202, null));

        // Run test and capture
        var msg = new NotificationMessage(
                NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                "template1", List.of("join.mailing.list@ddp.org"), null, null, null,    // no participant guid
                "study", "pepper", "from@ddp.org", "key", "salutation", List.of(), "url", 1L);
        spiedHandler.handleMessage(msg);

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mockSendGrid, times(1)).sendMail(captor.capture());

        // Assert the mail package itself
        var actualMail = captor.getValue();
        assertEquals(1, actualMail.getPersonalization().size());

        // Assert customizations
        var ps = actualMail.getPersonalization().get(0);
        assertEquals(1, ps.getTos().size());
        assertEquals(new Email("join.mailing.list@ddp.org", "join.mailing.list@ddp.org"), ps.getTos().get(0));
    }

    @Test
    public void testHandleMessage_ignored() {
        var spiedHandler = spy(handler);
        doReturn(true).when(spiedHandler).messageShouldBeIgnored(any(), any(), any());

        var msg = new NotificationMessage(
                NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                "template1", List.of("to@ddp.org"), "first", "last", "guid", "study",
                "pepper", "from@ddp.org", "key", "salutation", List.of(), "url", 1L);
        spiedHandler.handleMessage(msg);

        verify(mockSendGrid, never()).sendMail(any());
    }

    @Test
    public void testHandleMessage_templateLookupError() {
        // Setup mocks
        var spiedHandler = spy(handler);
        doReturn(false).when(spiedHandler).messageShouldBeIgnored(any(), any(), any());
        doReturn(Collections.emptyList()).when(spiedHandler).buildAttachments(any(), any(), any(), anyLong());
        doReturn(Collections.emptyMap()).when(spiedHandler).buildSubstitutions(any());

        // Setup error
        when(mockSendGrid.getTemplateActiveVersionId(any()))
                .thenReturn(ApiResult.thrown(new IOException("from test")));

        try {
            var msg = new NotificationMessage(
                    NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                    "abcxyz", List.of("to@ddp.org"), null, null, null,
                    "study", "pepper", "from@ddp.org", "key", "salutation", List.of(), "url", 1L);
            spiedHandler.handleMessage(msg);
            fail("expected exception not thrown");
        } catch (MessageHandlingException e) {
            assertTrue(e.getMessage().contains("abcxyz"));
            assertTrue(e.getMessage().contains("looking up version of template"));
            assertTrue(e.shouldRetry());
        }
    }

    @Test
    public void testHandleMessage_sendMailError() {
        // Setup mocks
        var spiedHandler = spy(handler);
        doReturn(false).when(spiedHandler).messageShouldBeIgnored(any(), any(), any());
        doReturn(Collections.emptyList()).when(spiedHandler).buildAttachments(any(), any(), any(), anyLong());
        doReturn(Collections.emptyMap()).when(spiedHandler).buildSubstitutions(any());
        when(mockSendGrid.getTemplateActiveVersionId(any())).thenReturn(ApiResult.ok(200, "v1"));

        var msg = new NotificationMessage(
                NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                "abcxyz", List.of("to@ddp.org"), null, null, null,
                "study", "pepper", "from@ddp.org", "key", "salutation", List.of(), "url", 1L);

        when(mockSendGrid.sendMail(any()))
                .thenReturn(ApiResult.thrown(new IOException("from test")));
        try {
            spiedHandler.handleMessage(msg);
            fail("expected exception not thrown");
        } catch (MessageHandlingException e) {
            assertTrue(e.getMessage().contains("abcxyz"));
            assertTrue(e.getMessage().contains("to@ddp.org"));
            assertTrue(e.shouldRetry());
        }

        reset(mockSendGrid);
        when(mockSendGrid.getTemplateActiveVersionId(any())).thenReturn(ApiResult.ok(200, "v1"));
        when(mockSendGrid.sendMail(any())).thenReturn(ApiResult.err(400, "some error response"));
        try {
            spiedHandler.handleMessage(msg);
            fail("expected exception not thrown");
        } catch (MessageHandlingException e) {
            assertTrue(e.getMessage().contains("abcxyz"));
            assertTrue(e.getMessage().contains("to@ddp.org"));
            assertTrue(e.getMessage().contains("some error response"));
            assertTrue(e.shouldRetry());
        }
    }
}
