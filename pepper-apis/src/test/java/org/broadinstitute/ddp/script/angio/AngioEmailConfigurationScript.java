package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.DATE_OF_BIRTH;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.DRAW_BLOOD_NO;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.DRAW_BLOOD_YES;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.FULL_NAME;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.TISSUE_SAMPLE_NO;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.TISSUE_SAMPLE_YES;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.ConsentFields.TODAY_DATE;
import static org.broadinstitute.ddp.constants.AngioPdfConstants.PdfFileLocations.CONSENT_PDF_LOCATION;
import static org.broadinstitute.ddp.housekeeping.HousekeepingSendgridEmailNotificationTest.template;
import static org.broadinstitute.ddp.script.angio.AngioConsentActivityCreationScript.BLOOD_SAMPLE_STABLE_ID;
import static org.broadinstitute.ddp.script.angio.AngioConsentActivityCreationScript.CONSENT_BIRTHDATE_STABLE_ID;
import static org.broadinstitute.ddp.script.angio.AngioConsentActivityCreationScript.CONSENT_SIGNATURE_STABLE_ID;
import static org.broadinstitute.ddp.script.angio.AngioConsentActivityCreationScript.TISSUE_SAMPLE_STABLE_ID;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiDsmNotificationEventType;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserNotificationPdf;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds various email configurations for angio.  Be sure to set
 * various -D variables for each template.
 */
@Ignore
public class AngioEmailConfigurationScript extends TxnAwareBaseTest {

    public static final String USER_NOT_ENROLLED_IN_STUDY_KEY = "userNotEnrolledInStudy";
    public static final String JOIN_MAILING_LIST_KEY = "joinMailingList";
    public static final String ANGIO_RELEASE_CREATED_KEY = "releaseCreated";
    public static final String EMAIL_CONFIG_LOCATION = "ddp.angio.emailConfigFile";
    public static final String PARTICIPANT_WELCOME_KEY = "participantWelcome";
    public static final String ANGIO_LOVED_ONE_WELCOME = "lovedOneWelcome";
    public static final String ANGIO_LOVED_ONE_COMPLETED = "lovedOneCompleted";
    private static final String CONSENT_CREATED_KEY = "consentCreated";
    private static final String RELEASE_COMPLETED_KEY = "releaseCompleted";
    private static final String RELEASE_REMINDER = "releaseReminder";
    private static final int ONE_WEEK_SECONDS = 7 * 60 * 60 * 24;
    private static final int TWO_WEEKS_SECONDS = ONE_WEEK_SECONDS * 2;
    private static final int THREE_WEEKS_SECONDS = ONE_WEEK_SECONDS * 3;
    private static final String ANGIO_CONSENT_PDF_CONFIGURATION_NAME = "ascproject-consent";
    private static final String ANGIO_CONSENT_PDF_CONFIGURATION_FILE_NAME = "ascproject-consent";
    private static final String SALIVA_RECEIVED_TEMPLATE = "salivaReceived";
    private static final String BLOOD_SENT_TEMPLATE = "bloodSent";
    private static final String BLOOD_RECEIVED_TEMPLATE = "bloodReceived";
    private static final String BLOOD_NOT_RECEIVED_TEMPLATE = "bloodNotReceived4Weeks";
    private static final String pex = "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"%s\")";
    private static final Logger LOG = LoggerFactory.getLogger(AngioEmailConfigurationScript.class);
    private static final String CONSENT_FIRST_REMINDER = "consentFirstReminder";
    private static final String CONSENT_SECOND_REMINDER = "consentSecondReminder";

    private static final String CONSENT_THIRD_REMINDER = "consentThirdReminder";

    private static Config emailConfig;
    private static String emailConfigFileLocation;
    private static StudyDto studyDto;

    @BeforeClass
    public static void setUp() {
        emailConfigFileLocation = System.getProperty(EMAIL_CONFIG_LOCATION);
        if (StringUtils.isBlank(emailConfigFileLocation)) {
            Assert.fail("Please set the config file for emails via -D" + EMAIL_CONFIG_LOCATION);
        }
        emailConfig = ConfigFactory.parseFile(new File(emailConfigFileLocation));
        TransactionWrapper.useTxn(handle -> {
            studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(AngioStudyCreationScript.ANGIO_STUDY_GUID);
        });
    }

    /**
     * Adds a new email configuration and returns the event_action_id
     */
    public static long addEmailForActivityStatus(Handle handle,
                                                 String studyGuid,
                                                 String templateLookupKey,
                                                 ActivityDto activityDto,
                                                 InstanceStatusType sendOnStatus,
                                                 InstanceStatusType cancelOnStatus,
                                                 Integer postAfterSeconds) {
        String template = emailConfig.getString(templateLookupKey);
        if (StringUtils.isBlank(template)) {
            Assert.fail("Please set the email template via " + templateLookupKey + " in file "
                    + emailConfigFileLocation);
        }


        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);

        Long cancelId = null;

        if (cancelOnStatus != null) {
            String cancelText = String.format(pex, studyGuid, activityDto.getActivityCode(), cancelOnStatus);
            Expression cancel = jdbiExpression.insertExpression(cancelText);
            cancelId = cancel.getId();
        }

        long triggerId = eventTriggerDao.insertStatusTrigger(activityDto.getActivityId(), sendOnStatus);
        long actionId = eventActionDao.insertNotificationAction(new SendgridEmailEventActionDto(template, "en"));


        long eventConfigId = jdbiEventConfig.insert(triggerId, actionId, studyDto.getId(), Instant.now().toEpochMilli(), 1,
                postAfterSeconds, null, cancelId, true, 1);

        LOG.info("Inserted event configuration id {} for triggering email template {}", eventConfigId, template);

        return actionId;

    }

    private void insertEventConfig(Handle handle, String key) throws Exception {
        String template = emailConfig.getString(key);
        if (StringUtils.isBlank(template)) {
            Assert.fail("Please set the " + key + " in file "
                    + emailConfigFileLocation);
        }

        SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(template, "en");

        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);
        long emailActionId = eventActionDao.insertNotificationAction(eventAction);

        long eventTriggerId = -1;
        switch (key) {
            case JOIN_MAILING_LIST_KEY:
                eventTriggerId = eventTriggerDao.insertMailingListTrigger();
                break;
            case USER_NOT_ENROLLED_IN_STUDY_KEY:
                eventTriggerId = eventTriggerDao.insertUserNotInStudyTrigger();
                break;
            default:
                throw new Exception("This key is currently not supported for event configurations: " + key);
        }

        long insertedEventConfigId = jdbiEventConfig.insert(eventTriggerId, emailActionId, studyDto.getId(),
                Instant.now().toEpochMilli(), null, null, null,
                null, true, 1);

        LOG.info("Inserted event configuration {} with template {}",
                insertedEventConfigId, template);
    }

    @Test
    public void insertJoinMailingListEmail() throws Exception {
        TransactionWrapper.useTxn(handle ->
                insertEventConfig(handle, JOIN_MAILING_LIST_KEY)
        );
    }

    @Test
    public void insertAboutYouCreatedEmail() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioAboutYouActivityCreationScript.ACTIVITY_CODE
            );
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID,
                    PARTICIPANT_WELCOME_KEY, activityDto.get(), InstanceStatusType
                            .CREATED, null, 0);
        });
    }

    @Test
    public void insertConsentCreatedEmail() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioConsentActivityCreationScript.ACTIVITY_CODE
            );
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID,
                    CONSENT_CREATED_KEY, activityDto.get(), InstanceStatusType
                            .CREATED, null, 0);
        });
    }

    @Test
    public void insertReleaseCompletedEmail() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioReleaseActivityCreationScript.ACTIVITY_CODE
            );
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID,
                    RELEASE_COMPLETED_KEY, activityDto.get(), InstanceStatusType.COMPLETE, null, 0);
        });
    }

    @Test
    public void insertLovedOneCreatedEmail() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioLovedOneActivityCreationScript.ACTIVITY_CODE
            );
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, ANGIO_LOVED_ONE_WELCOME,
                    activityDto.get(), InstanceStatusType
                            .CREATED, null, 0);
        });
    }

    @Test
    public void insertLovedOneCompletedEmail() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioLovedOneActivityCreationScript.ACTIVITY_CODE
            );
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, ANGIO_LOVED_ONE_COMPLETED,
                    activityDto.get(), InstanceStatusType
                            .COMPLETE, null, 0);
        });
    }

    private void insertDsmNotificationFor(Handle handle, String dsmEventName, String emailTemplate) {
        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
        long triggerId = eventTriggerDao.insertDsmNotificationTrigger(dsmEventName);
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        long actionId = eventActionDao.insertNotificationAction(new SendgridEmailEventActionDto(emailTemplate, "en"));

        long eventConfigId = jdbiEventConfig.insert(triggerId, actionId, studyDto.getId(), Instant.now().toEpochMilli(), null,
                0, null, null, true, 1);

        LOG.info("Inserted event configuration id {} for triggering {} email template {}",
                eventConfigId,
                template);
    }

    @Test
    public void insertSalivaReceivedEmail() {
        String template = emailConfig.getString(SALIVA_RECEIVED_TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            insertDsmNotificationFor(handle, JdbiDsmNotificationEventType.SALIVA_RECEIVED, template);
        });
    }

    @Test
    public void insertBloodSentEmail() {
        String template = emailConfig.getString(BLOOD_SENT_TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            insertDsmNotificationFor(handle, JdbiDsmNotificationEventType.BLOOD_SENT, template);
        });
    }

    @Test
    public void insertBloodReceivedEmail() {
        String template = emailConfig.getString(BLOOD_RECEIVED_TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            insertDsmNotificationFor(handle, JdbiDsmNotificationEventType.BLOOD_RECEIVED, template);
        });
    }

    @Test
    public void insertBloodNotReceivedEmail() {
        String template = emailConfig.getString(BLOOD_NOT_RECEIVED_TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            insertDsmNotificationFor(handle, JdbiDsmNotificationEventType.BLOOD_NOT_RECEIVED_4_WEEKS, template);
        });
    }

    @Test
    public void insertUserNotEnrolledInStudyEmail() throws Exception {
        TransactionWrapper.useTxn(handle ->
                insertEventConfig(handle, USER_NOT_ENROLLED_IN_STUDY_KEY)
        );
    }

    @Test
    public void insertParticipantReleaseReminders() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioReleaseActivityCreationScript.ACTIVITY_CODE
            );

            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, RELEASE_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE, ONE_WEEK_SECONDS);
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, RELEASE_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE, TWO_WEEKS_SECONDS);
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, RELEASE_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE,
                    THREE_WEEKS_SECONDS);
        });
    }

    @Test
    public void insertParticipantConsentReminders() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class).findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioConsentActivityCreationScript.ACTIVITY_CODE
            );

            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, CONSENT_FIRST_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE, ONE_WEEK_SECONDS);
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, CONSENT_SECOND_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE, TWO_WEEKS_SECONDS);
            addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID, CONSENT_THIRD_REMINDER,
                    activityDto.get(), InstanceStatusType.CREATED, InstanceStatusType.COMPLETE,
                    THREE_WEEKS_SECONDS);
        });
    }

    @Test
    public void insertReleaseCreatedEmail() throws IOException {
        TransactionWrapper.useTxn(handle -> {
            JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

            ActivityDto consentDto = jdbiActivity.findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioConsentActivityCreationScript.ACTIVITY_CODE
            ).get();
            ActivityDto releaseDto = jdbiActivity.findActivityByStudyIdAndCode(
                    studyDto.getId(), AngioReleaseActivityCreationScript.ACTIVITY_CODE
            ).get();

            ActivityVersionDto consentVersion = handle.attach(JdbiActivityVersion.class)
                    .getActiveVersion(consentDto.getActivityId()).get();

            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(AngioStudyCreationScript.ANGIO_USER_GUID);
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), userId,
                    "Made pdf config: " + ANGIO_CONSENT_PDF_CONFIGURATION_NAME
                            + " for study:" + studyDto.getGuid() + " and user: " + userId);

            List<String> questionStableIds = Arrays.asList(BLOOD_SAMPLE_STABLE_ID,
                    TISSUE_SAMPLE_STABLE_ID,
                    CONSENT_SIGNATURE_STABLE_ID,
                    CONSENT_BIRTHDATE_STABLE_ID);

            List<String> fieldValues = Arrays.asList(DRAW_BLOOD_YES,
                    DRAW_BLOOD_NO,
                    TISSUE_SAMPLE_YES,
                    TISSUE_SAMPLE_NO,
                    FULL_NAME,
                    DATE_OF_BIRTH,
                    TODAY_DATE);

            byte[] pdfToByteArray = IOUtils.toByteArray(new FileInputStream(CONSENT_PDF_LOCATION));
            CustomTemplate customTemplate = new CustomTemplate(pdfToByteArray);
            customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(0),
                    consentDto.getActivityId(), questionStableIds.get(0), false));
            customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(1),
                    consentDto.getActivityId(), questionStableIds.get(0), true));
            customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(2),
                    consentDto.getActivityId(), questionStableIds.get(1), false));
            customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(3),
                    consentDto.getActivityId(), questionStableIds.get(1), true));
            customTemplate.addSubstitution(new AnswerSubstitution(fieldValues.get(4),
                    consentDto.getActivityId(), QuestionType.TEXT, questionStableIds.get(2)));
            customTemplate.addSubstitution(new AnswerSubstitution(fieldValues.get(5),
                    consentDto.getActivityId(), QuestionType.DATE, questionStableIds.get(3)));
            customTemplate.addSubstitution(new ActivityDateSubstitution(fieldValues.get(6), consentDto.getActivityId()));

            PdfConfigInfo info = new PdfConfigInfo(studyDto.getId(),
                    ANGIO_CONSENT_PDF_CONFIGURATION_NAME, ANGIO_CONSENT_PDF_CONFIGURATION_FILE_NAME);

            PdfVersion version = new PdfVersion("v1", revId);
            version.addDataSource(new PdfActivityDataSource(consentDto.getActivityId(), consentVersion.getId()));

            PdfConfiguration config = new PdfConfiguration(info, version);
            config.addTemplate(customTemplate);

            long configurationId = handle.attach(PdfDao.class).insertNewConfig(config);
            long eventActionId = addEmailForActivityStatus(handle, AngioStudyCreationScript.ANGIO_STUDY_GUID,
                    ANGIO_RELEASE_CREATED_KEY, releaseDto, InstanceStatusType.CREATED, null, 0);
            long pdfNotificationId = handle.attach(JdbiUserNotificationPdf.class).insert(configurationId,
                    eventActionId, true);
            LOG.info("Inserted pdf notification configuration {}", pdfNotificationId);

            long mappingId = handle.attach(JdbiStudyPdfMapping.class)
                    .insert(studyDto.getId(), PdfMappingType.CONSENT, configurationId);
            LOG.info("Created pdf mapping for consentpdf with mapping id={}", mappingId);
        });
    }

    @Test
    public void insertOnDemandConsentEmailConfig() {
        String template = emailConfig.getString("newOnDemandActivity");
        TransactionWrapper.useTxn(handle -> {
            EventActionDao eventActionDao = handle.attach(EventActionDao.class);
            EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);
            JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

            SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(template, "en");
            long emailActionId = eventActionDao.insertNotificationAction(eventAction);

            long studyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(AngioStudyCreationScript.ANGIO_STUDY_GUID).get();
            long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(studyId,
                    AngioFollowupConsentCreationScript.ACTIVITY_CODE).get();
            long eventTriggerId = eventTriggerDao.insertStatusTrigger(activityId, InstanceStatusType.CREATED);

            long configId = jdbiEventConfig.insert(eventTriggerId, emailActionId, studyDto.getId(),
                    Instant.now().toEpochMilli(), 10, null, null,
                    null, true, 1);

            LOG.info("Inserted event configuration {} for on-demand activity with template {}", configId, template);
        });
    }
}
