package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_SALUTATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredEventListener;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.sendgrid.Attachments;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventTrigger;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUserNotificationPdf;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.housekeeping.handler.EmailNotificationHandler;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.transformers.DateTimeFormatUtils;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.PdfLicenseUtil;
import org.broadinstitute.ddp.util.PdfTestingUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class EmailNotificationHandlerTest extends TxnAwareBaseTest {

    public static final String TEMPLATE_SUBSTITUTION_KEY = "foo";
    public static final String TEMPLATE_SUBSTITUTION_VALUE = "bar";
    private static SendGrid sendGrid;
    private static EmailNotificationHandler enh;
    private static String templatesRequest = "{ \"versions\" : [{ \"active\" : 1, \"id\" : 1 }]}";
    private PdfTestingUtil.PdfInfo pdfInfo;

    @Before
    public void setUpMock() throws Exception {
        PdfLicenseUtil.loadITextLicense();
        sendGrid = Mockito.mock(SendGrid.class);
        enh = new EmailNotificationHandler(sendGrid, new PdfService(), new PdfBucketService(cfg), new PdfGenerationService());

        // todo this is copy pasted from HousekeepingTest.setupTransactionWrappersAndBootHousekeeping(),
        // needs to be factored out to something cleaner

        String pepperDbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
        int maxPepperConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String housekeepingDbUrl = cfg.getString(TransactionWrapper.DB.HOUSEKEEPING.getDbUrlConfigKey());
        int maxHousekeepingConnections = cfg.getInt(ConfigFile.HOUSEKEEPING_NUM_POOLED_CONNECTIONS);

        LiquibaseUtil.runLiquibase(pepperDbUrl, TransactionWrapper.DB.APIS);
        LiquibaseUtil.runLiquibase(housekeepingDbUrl, TransactionWrapper.DB.HOUSEKEEPING);

        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                        maxPepperConnections, pepperDbUrl),
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.HOUSEKEEPING,
                        maxHousekeepingConnections, housekeepingDbUrl));
    }

    /**
     * Inserts dummy event_configuration data so that housekeeping
     * can join between the pdf configuration and a notification.
     * Returns the generated event configuration id.
     */
    private long insertEventConfiguration(long pdfConfigId, long studyId) {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
            EventActionDao eventActionDao = handle.attach(EventActionDao.class);
            JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

            long triggerId = handle.attach(JdbiEventTrigger.class).insert(EventTriggerType.WORKFLOW_STATE);
            long actionId = eventActionDao.insertNotificationAction(new SendgridEmailEventActionDto("bogus", "en"));
            long eventConfigurationId = jdbiEventConfig.insert(triggerId, actionId, studyId,
                    Instant.now()
                            .toEpochMilli(),
                    1,
                    0, null, null, true, 1);
            JdbiUserNotificationPdf jdbiUserNotificationPdf = handle.attach(JdbiUserNotificationPdf.class);
            jdbiUserNotificationPdf.insert(pdfConfigId, actionId, true);
            return eventConfigurationId;
        });
    }

    @After
    public void cleanup() {
        if (pdfInfo != null) {
            PdfTestingUtil.removeConfigurationData(pdfInfo);
            pdfInfo = null;
        }
    }

    @Test
    public void test_GivenTheUserEnrolledAndExited_WhenMessageIsHandled_NoEmailIsSent() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        long userStudyEnrollmentId = TransactionWrapper.withTxn(
                TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                        pdfInfo.getData().getUserGuid(),
                        pdfInfo.getData().getStudyGuid(),
                        EnrollmentStatusType.EXITED_AFTER_ENROLLMENT
                )
        );
        enh.handleMessage(createNotificationMessage(pdfInfo));
        verify(sendGrid, times(0)).api(any(Request.class));
        TransactionWrapper.useTxn(
                TransactionWrapper.DB.APIS,
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class).deleteById(userStudyEnrollmentId);
                }
        );
    }

    @Test
    public void test_GivenTheUserExited_WhenMessageIsHandled_NoEmailIsSent() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        long userStudyEnrollmentId = TransactionWrapper.withTxn(
                TransactionWrapper.DB.APIS,
                handle -> {
                    return handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                            pdfInfo.getData().getUserGuid(),
                            pdfInfo.getData().getStudyGuid(),
                            EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT
                    );
                }
        );
        enh.handleMessage(createNotificationMessage(pdfInfo));
        verify(sendGrid, times(0)).api(any(Request.class));
        TransactionWrapper.useTxn(
                TransactionWrapper.DB.APIS,
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class).deleteById(userStudyEnrollmentId);
                }
        );
    }

    @Test
    public void test_GivenTheUserAskedNotToContact_WhenMessageIsHandled_NoEmailIsSent() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        updateDoNotContactInProfile(true, pdfInfo.getData().getUserId());
        enh.handleMessage(createNotificationMessage(pdfInfo));
        verify(sendGrid, times(0)).api(any(Request.class));
        updateDoNotContactInProfile(null, pdfInfo.getData().getUserId());
    }

    @Test
    public void test_GivenTheUserHasNoContactPreference_WhenMessageIsHandled_EmailIsSent() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        updateDoNotContactInProfile(null, pdfInfo.getData().getUserId());
        enh.handleMessage(createNotificationMessage(pdfInfo));
        verify(sendGrid, times(2)).api(any(Request.class));
    }

    @Test
    public void test_GivenTheUserWantsToBeContacted_WhenMessageIsHandled_EmailIsSent() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        updateDoNotContactInProfile(false, pdfInfo.getData().getUserId());
        enh.handleMessage(createNotificationMessage(pdfInfo));
        verify(sendGrid, times(2)).api(any(Request.class));
        updateDoNotContactInProfile(null, pdfInfo.getData().getUserId());
    }

    @Test
    public void testHandleMessage_forNonUsersFromMailingList() throws Exception {
        // Generate test data and setup proper event configuration for "join mailing list"
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            if (pdfInfo.getEventConfigurationId() != null) {
                handle.attach(JdbiEventConfiguration.class).deleteById(pdfInfo.getEventConfigurationId());
                handle.attach(JdbiUserNotificationPdf.class).deleteByPdfDocumentConfigurationId(pdfInfo.getConfigurationId());
            }
            long triggerId = handle.attach(EventTriggerDao.class).insertMailingListTrigger();
            long emailActionId = handle.attach(EventActionDao.class)
                    .insertNotificationAction(new SendgridEmailEventActionDto("blah", "en"));
            long configId = handle.attach(JdbiEventConfiguration.class)
                    .insert(triggerId, emailActionId, pdfInfo.getData().getStudyId(), Instant.now().toEpochMilli(),
                            null, null, null, null, true, 1);
            pdfInfo.setEventConfigurationId(configId);
        });

        Mockito.when(sendGrid.api(any(Request.class)))
                .thenReturn(new Response(HttpStatus.SC_ACCEPTED, templatesRequest, null));

        NotificationMessage msg = new NotificationMessage(
                NotificationType.EMAIL,
                null,
                "blah",
                Arrays.asList("fake.join.mail.list@datadonationplatform.org"),
                null,
                null,
                null,
                pdfInfo.getData().getStudyGuid(),
                "Test Pepper",
                "fake@datadonationplatform.org",
                "key",
                "test patient",
                new ArrayList<>(),
                "https://fake.datadonationplatform.org",
                pdfInfo.getEventConfigurationId());

        enh.handleMessage(msg);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid, times(2)).api(captor.capture());
        assertEquals(Method.GET, captor.getAllValues().get(0).getMethod());
        assertTrue(captor.getAllValues().get(0).getEndpoint().contains("templates"));
        assertEquals(Method.POST, captor.getAllValues().get(1).getMethod());
        assertTrue(captor.getAllValues().get(1).getEndpoint().contains("mail/send"));
        assertTrue(captor.getAllValues().get(1).getBody().contains("fake@datadonationplatform.org"));
    }

    @Test
    public void testHandle() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        long eventConfigurationId = insertEventConfiguration(pdfInfo.getConfigurationId(), pdfInfo.getData().getStudyId());
        pdfInfo.setEventConfigurationId(eventConfigurationId);
        Mockito.when(sendGrid.api(any(Request.class)))
                .thenReturn(new Response(HttpStatus.SC_ACCEPTED, templatesRequest, null));

        String webBaseUrl = "https://fake.datadonationplatform.org";
        String firstName = "first";
        String lastName = "last";
        String defaultSalutation = "hi test patient!";
        Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();
        templateSubstitutions.add(new NotificationTemplateSubstitutionDto(TEMPLATE_SUBSTITUTION_KEY,
                TEMPLATE_SUBSTITUTION_VALUE));
        templateSubstitutions
                .add(new NotificationTemplateSubstitutionDto(DDP_ACTIVITY_INSTANCE_GUID, pdfInfo.getTestActivityInstanceGuid()));

        enh.handleMessage(new NotificationMessage(
                NotificationType.EMAIL,
                null,
                "Very Good Template Key",
                new ArrayList<>(),
                firstName,
                lastName,
                pdfInfo.getData().getUserGuid(),
                pdfInfo.getData().getStudyGuid(),
                null,
                null,
                null,
                defaultSalutation,
                templateSubstitutions,
                webBaseUrl,
                pdfInfo.getEventConfigurationId()
        ));

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid, times(2)).api(requestArgumentCaptor.capture());

        List<Request> arguments = requestArgumentCaptor.getAllValues();
        boolean found = false;

        for (Request request : arguments) {
            if (request.getMethod().equals(Method.POST)) {
                String bodyToString = request.getBody();
                Mail mail = new ObjectMapper().readValue(bodyToString, Mail.class);

                List<Personalization> substitutions = mail.getPersonalization();
                assertFalse(substitutions.isEmpty());

                for (Personalization substitution : substitutions) {
                    Map<String, String> templateSubs = substitution.getSubstitutions();
                    assertEquals(webBaseUrl, templateSubs.get(DDP_BASE_WEB_URL));
                    assertEquals(EmailNotificationHandler.generateSalutation(firstName, lastName, defaultSalutation),
                            templateSubs.get(DDP_SALUTATION));
                    assertEquals(TEMPLATE_SUBSTITUTION_VALUE, templateSubs.get(TEMPLATE_SUBSTITUTION_KEY));
                }

                assertEquals(1, mail.getAttachments().size());
                assertTrue(mail.getAttachments().get(0).getType().contains("pdf"));
                assertEquals(pdfInfo.getPdfConfiguration().getFilename() + ".pdf", mail.getAttachments().get(0).getFilename());
                found = true;
            }
        }

        assertTrue("Did not find request for emailing pdf attachment!", found);
    }

    @Test
    public void testHandle_doesNotIncludeFirstNameLastNameSubstitutionsWhenNoName() throws Exception {
        pdfInfo = PdfTestingUtil.makePhysicianInstitutionPdf();
        long eventConfigurationId = insertEventConfiguration(pdfInfo.getConfigurationId(), pdfInfo.getData().getStudyId());
        pdfInfo.setEventConfigurationId(eventConfigurationId);
        Mockito.when(sendGrid.api(any(Request.class)))
                .thenReturn(new Response(HttpStatus.SC_ACCEPTED, templatesRequest, null));

        String webBaseUrl = "https://fake.datadonationplatform.org";
        String firstName = null;
        String lastName = null;
        String defaultSalutation = "hi test patient!";
        Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();
        templateSubstitutions.add(new NotificationTemplateSubstitutionDto(TEMPLATE_SUBSTITUTION_KEY,
                TEMPLATE_SUBSTITUTION_VALUE));
        templateSubstitutions
                .add(new NotificationTemplateSubstitutionDto(DDP_ACTIVITY_INSTANCE_GUID, pdfInfo.getTestActivityInstanceGuid()));

        enh.handleMessage(new NotificationMessage(
                NotificationType.EMAIL,
                null,
                "Very Good Template Key",
                new ArrayList<>(),
                firstName,
                lastName,
                pdfInfo.getData().getUserGuid(),
                pdfInfo.getData().getStudyGuid(),
                null,
                null,
                null,
                defaultSalutation,
                templateSubstitutions,
                webBaseUrl,
                pdfInfo.getEventConfigurationId()
        ));

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);


        List<Request> arguments = requestArgumentCaptor.getAllValues();

        for (Request request : arguments) {
            if (request.getMethod().equals(Method.POST)) {
                String bodyToString = request.getBody();
                Mail mail = new ObjectMapper().readValue(bodyToString, Mail.class);

                List<Personalization> substitutions = mail.getPersonalization();
                assertFalse(substitutions.isEmpty());

                for (Personalization substitution : substitutions) {
                    Map<String, String> templateSubs = substitution.getSubstitutions();

                    assertEquals(EmailNotificationHandler.generateSalutation(firstName, lastName, defaultSalutation),
                            templateSubs.get(DDP_SALUTATION));
                }
            }
        }

    }

    @Test
    public void testHandleConsentPdf() throws Exception {
        AtomicLong configurationId = new AtomicLong();
        TestDataSetupUtil.GeneratedTestData otherdata =
                TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
                    TestDataSetupUtil.GeneratedTestData secondTest = TestDataSetupUtil.generateBasicUserTestData(handle);
                    TestDataSetupUtil.addTestConsent(handle, secondTest);
                    TestDataSetupUtil.answerTestConsent(handle,
                            true,
                            true,
                            true,
                            PdfTestingUtil.TEST_DATE_VALUE.getDay(),
                            PdfTestingUtil.TEST_DATE_VALUE.getMonth(),
                            PdfTestingUtil.TEST_DATE_VALUE.getYear(),
                            secondTest);
                    secondTest.setPdfConfigInfo(TestDataSetupUtil.createAngioConsentPdfForm(handle, secondTest));
                    configurationId.set(secondTest.getPdfConfigInfo().getId());
                    System.out.println("consent config id: " + configurationId);
                    return secondTest;
                });
        long eventConfigurationId = insertEventConfiguration(configurationId.get(), otherdata.getStudyId());

        Mockito.when(sendGrid.api(any(Request.class))).thenReturn(new Response(HttpStatus.SC_ACCEPTED, templatesRequest, null));

        String webBaseUrl = "https://fake.datadonationplatform.org";
        String firstName = "first";
        String lastName = "last";
        String defaultSalutation = "test patient";

        Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();
        templateSubstitutions.add(new NotificationTemplateSubstitutionDto(TEMPLATE_SUBSTITUTION_KEY,
                TEMPLATE_SUBSTITUTION_VALUE));
        templateSubstitutions.add(new NotificationTemplateSubstitutionDto(DDP_ACTIVITY_INSTANCE_GUID,
                otherdata.getConsentActivityInstanceGuid()));

        enh.handleMessage(new NotificationMessage(
                NotificationType.EMAIL,
                null,
                "Very Good Template Key",
                new ArrayList<>(),
                firstName,
                lastName,
                otherdata.getUserGuid(),
                otherdata.getStudyGuid(),
                null,
                null,
                null,
                defaultSalutation,
                templateSubstitutions,
                webBaseUrl,
                eventConfigurationId
        ));

        ArgumentCaptor<Request> requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid, times(2)).api(requestArgumentCaptor.capture());

        List<Request> arguments = requestArgumentCaptor.getAllValues();
        boolean found = false;
        Attachments theAttachment = null;

        for (Request request : arguments) {
            if (request.getMethod().equals(Method.POST)) {
                String bodyToString = request.getBody();
                Mail mail = new ObjectMapper().readValue(bodyToString, Mail.class);
                assertEquals(mail.getAttachments().size(), 1);
                assertTrue(mail.getAttachments().get(0).getType().contains("pdf"));
                found = true;
                theAttachment = mail.getAttachments().get(0);
            }
        }

        assertEquals(otherdata.getPdfConfigInfo().getFilename() + ".pdf", theAttachment.getFilename());
        PdfReader reader = new PdfReader(new ByteArrayInputStream(Base64.getDecoder()
                .decode(theAttachment.getContent().getBytes())));

        String actualText = getPdfText(reader);

        assertTrue("Did not find request for emailing pdf attachment!", found);

        //checking for values (no way to check for checkboxes checked off I think)
        assertTrue(actualText.contains(PdfTestingUtil.TEST_DATE_VALUE.toDefaultDateFormat()));

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            ActivityInstanceStatusDao instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
            Optional<ActivityInstanceStatusDto> optionalStatus =
                    instanceStatusDao.getLatestStatus(otherdata.getConsentActivityInstanceGuid(), InstanceStatusType.COMPLETE);
            if (optionalStatus.isPresent()) {
                assertTrue(actualText
                        .contains(DateTimeFormatUtils.convertUtcMillisToDefaultDateString(optionalStatus.get().getUpdatedAt())));
            }
        });
    }

    public String getPdfText(PdfReader pdf) {
        PdfDocument pdfDoc = new PdfDocument(pdf);

        FilteredEventListener listener = new FilteredEventListener();


        StringBuilder actualText = new StringBuilder();

        PdfCanvasProcessor processor = new PdfCanvasProcessor(listener);
        for (int currPage = 1; currPage <= pdfDoc.getNumberOfPages(); currPage++) {
            LocationTextExtractionStrategy extractionStrategy =
                    listener.attachEventListener(new LocationTextExtractionStrategy());
            processor.processPageContent(pdfDoc.getPage(currPage));
            actualText.append(extractionStrategy.getResultantText());
        }

        pdfDoc.close();
        return actualText.toString();
    }

    private NotificationMessage createNotificationMessage(PdfTestingUtil.PdfInfo pdfInfo) throws Exception {
        long eventConfigurationId = insertEventConfiguration(pdfInfo.getConfigurationId(), pdfInfo.getData().getStudyId());
        pdfInfo.setEventConfigurationId(eventConfigurationId);
        Mockito.when(sendGrid.api(any(Request.class)))
                .thenReturn(new Response(HttpStatus.SC_ACCEPTED, templatesRequest, null));

        String webBaseUrl = "https://fake.datadonationplatform.org";
        String firstName = "first";
        String lastName = "last";
        String defaultSalutation = "hi test patient!";
        Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();
        templateSubstitutions.add(new NotificationTemplateSubstitutionDto(TEMPLATE_SUBSTITUTION_KEY,
                TEMPLATE_SUBSTITUTION_VALUE));
        templateSubstitutions
                .add(new NotificationTemplateSubstitutionDto(DDP_ACTIVITY_INSTANCE_GUID, pdfInfo.getTestActivityInstanceGuid()));

        return new NotificationMessage(
                NotificationType.EMAIL,
                null,
                "Very Good Template Key",
                new ArrayList<>(),
                firstName,
                lastName,
                pdfInfo.getData().getUserGuid(),
                pdfInfo.getData().getStudyGuid(),
                null,
                null,
                null,
                defaultSalutation,
                templateSubstitutions,
                webBaseUrl,
                pdfInfo.getEventConfigurationId()
        );
    }

    private void updateDoNotContactInProfile(Boolean value, long userId) {
        TransactionWrapper.useTxn(
                TransactionWrapper.DB.APIS,
                handle -> {
                    handle.attach(JdbiProfile.class).updateDoNotContact(
                            value,
                            userId
                    );
                }
        );
    }

}
