package org.broadinstitute.ddp.util;

import static org.apache.commons.lang.time.DateUtils.MILLIS_PER_SECOND;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import com.auth0.json.mgmt.users.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants.MedicalProviderTable;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiCountrySubnationalDivision;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserLegacyInfo;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.migration.AboutYouSurvey;
import org.broadinstitute.ddp.model.migration.ConsentSurvey;
import org.broadinstitute.ddp.model.migration.DatstatParticipantData;
import org.broadinstitute.ddp.model.migration.DatstatSurveyData;
import org.broadinstitute.ddp.model.migration.FollowupConsentSurvey;
import org.broadinstitute.ddp.model.migration.Gen2Survey;
import org.broadinstitute.ddp.model.migration.Institution;
import org.broadinstitute.ddp.model.migration.LovedOneSurvey;
import org.broadinstitute.ddp.model.migration.MailingListData;
import org.broadinstitute.ddp.model.migration.MailingListDatum;
import org.broadinstitute.ddp.model.migration.ParticipantData;
import org.broadinstitute.ddp.model.migration.Physician;
import org.broadinstitute.ddp.model.migration.ReleaseSurvey;
import org.broadinstitute.ddp.model.migration.SurveyAddress;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.util.gen2.enums.LovedOneRelationTo;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parse, load and insert Participant data into pepper-db
 */
public class DataLoader {
    public static final String HEADFACENECK = "HEADFACENECK";
    public static final String SCALP = "SCALP";
    public static final String BREAST = "BREAST";
    public static final String HEART = "HEART";
    public static final String LIVER = "LIVER";
    public static final String SPLEEN = "SPLEEN";
    public static final String LUNG = "LUNG";
    public static final String BRAIN = "BRAIN";
    public static final String LYMPH = "LYMPH";
    public static final String BONELIMB = "BONELIMB";
    public static final String ABDOMINAL = "ABDOMINAL";
    public static final String OTHER = "OTHER";
    public static final String NED = "NED";
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String DK = "DK";
    public static final String BEFORE = "BEFORE";
    public static final String AFTER = "AFTER";
    public static final String BOTH = "BOTH";
    public static final Integer OPTION_SELECTED = 1;
    public static final String ABOUTYOU_ACTIVITY_CODE = "ANGIOABOUTYOU";
    public static final String RELEASE_ACTIVITY_CODE = "ANGIORELEASE";
    public static final String CONSENT_ACTIVITY_CODE = "ANGIOCONSENT";
    public static final String FOLLOWUP_CONSENT_ACTIVITY_CODE = "followupconsent";
    public static final String LOVEDONE_ACTIVITY_CODE = "ANGIOLOVEDONE";
    private static final Logger LOG = LoggerFactory.getLogger(DataLoader.class);
    private static final String USER_MEDICAL_PROVIDER = "USER_MEDICAL_PROVIDER";
    private static final String USER_MEDICAL_PROVIDER_GUID = "USER_MEDICAL_PROVIDER_GUID";
    private static final String DEFAULT_PREFERRED_LANGUAGE_CODE = "en";
    private static final String DATSTAT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final LocalDate DEFAULT_MIGRATION_KIT_CREATE_DATE =
            LocalDate.parse("01/01/2016", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final String DOB_DATE_FORMAT = "d/M/yyyy";
    private static final String DEFAULT_DSM_BIOPSY_GUID = "1";
    private static final int DSM_DEFAULT_ON_DEMAND_TRIGGER_ID = -2;

    void loadMailingListData(Handle handle, MailingListData data, String studyCode) {
        LOG.info("load mailinglist");
        JdbiMailingList dao = handle.attach(JdbiMailingList.class);
        Long dateCreatedMillis;
        for (MailingListDatum mailingItem : data.getMailingListData()) {
            dateCreatedMillis = null;
            if (mailingItem.getDateCreated() != null) {
                dateCreatedMillis = mailingItem.getDateCreated() * MILLIS_PER_SECOND;
            }
            dao.insertByStudyGuidIfNotStoredAlready(mailingItem.getFirstName(), mailingItem.getLastName(), mailingItem.getEmail(),
                    studyCode, null, dateCreatedMillis);
        }
    }

    String loadParticipantData(Handle handle, Config cfg, ParticipantData participantData, String studyGuid,
                               OLCService olcService) throws Exception {

        DatstatParticipantData data = participantData.getParticipantUser().getDatstatparticipantdata();

        JdbiUser jdbiUser = handle.attach(JdbiUser.class);
        String userGuid = jdbiUser.getUserGuidByAltpid(data.getDatstatAltpid());
        if (userGuid != null) {
            LOG.warn("Looks like  Participant data already loaded: " + userGuid);
            return userGuid;
            //watch out.. early return
        }

        UserDto pepperUser = createUserForLegacyParticipant(handle, cfg, data);
        userGuid = pepperUser.getUserGuid();

        JdbiLanguageCode jdbiLanguageCode = handle.attach(JdbiLanguageCode.class);
        UserProfileDao profileDao = handle.attach(UserProfileDao.class);
        addUserProfile(pepperUser, data, jdbiLanguageCode, profileDao);

        JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
        MailAddress address = addUserAddress(handle, pepperUser,
                data,
                participantData.getParticipantUser().getDatstatsurveydata().getReleaseSurvey(),
                jdbiMailAddress,
                olcService);

        if (data.getDdpSpitKitRequestId() != null) {
            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            DsmKitRequestDao dsmKitRequestDao = handle.attach(DsmKitRequestDao.class);
            addKitDetails(dsmKitRequestDao,
                    kitTypeDao,
                    pepperUser.getUserId(),
                    address.getId(),
                    data,
                    studyGuid);
        }

        String ddpCreated = data.getDdpCreated();
        Long ddpCreatedAt = null;
        boolean couldNotParse = false;

        try {
            if (ddpCreated != null) {
                Instant instant = Instant.parse(ddpCreated);
                if (instant != null) {
                    ddpCreatedAt = instant.toEpochMilli();
                }
            }
        } catch (DateTimeParseException e) {
            couldNotParse = true;
        }

        if (couldNotParse || ddpCreatedAt == null) {
            throw new RuntimeException("Could not figure out registration date for user: " + userGuid);
        }

        handle.attach(JdbiUserStudyEnrollment.class)
                .changeUserStudyEnrollmentStatus(userGuid, studyGuid, EnrollmentStatusType.REGISTERED, ddpCreatedAt);

        LOG.info("user guid: " + pepperUser.getUserGuid());
        return pepperUser.getUserGuid();
    }

    public String createActivityInstance(ParticipantData participantData,
                                         String participantGuid, long studyId, String activityCode,
                                         JdbiActivity jdbiActivity,
                                         JdbiActivityVersion jdbiActivityVersion,
                                         ActivityInstanceDao activityInstanceDao,
                                         ActivityInstanceStatusDao activityInstanceStatusDao,
                                         JdbiActivityInstance jdbiActivityInstance) throws Exception {
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();

        Gen2Survey gen2Survey = null;
        String surveyVersion = null;
        switch (activityCode) {
            case LOVEDONE_ACTIVITY_CODE:
                gen2Survey = surveyData.getLovedOneSurvey();
                surveyVersion = surveyData.getLovedOneSurvey().getSurveyversion();
                break;

            case ABOUTYOU_ACTIVITY_CODE:
                gen2Survey = surveyData.getAboutYouSurvey();
                surveyVersion = surveyData.getAboutYouSurvey().getSurveyversion();
                break;

            case RELEASE_ACTIVITY_CODE:
                gen2Survey = surveyData.getReleaseSurvey();
                surveyVersion = surveyData.getReleaseSurvey().getSurveyversion();
                break;

            case CONSENT_ACTIVITY_CODE:
                gen2Survey = surveyData.getConsentSurvey();
                surveyVersion = surveyData.getConsentSurvey().getSurveyversion();
                break;

            case FOLLOWUP_CONSENT_ACTIVITY_CODE:
                gen2Survey = surveyData.getFollowupConsentSurvey();
                surveyVersion = surveyData.getFollowupConsentSurvey().getSurveyversion();
                break;

            default:
                LOG.error("Invalid activity code passed: {} ", activityCode);
                break;
        }

        if (gen2Survey != null) {
            Integer submissionId = gen2Survey.getDatstatSubmissionid();
            String sessionId = gen2Survey.getDatstatSessionid();
            String ddpCreated = gen2Survey.getDdpCreated();
            String ddpCompleted = gen2Survey.getDdpFirstcompleted();
            String ddpLastUpdated = gen2Survey.getDdpLastupdated();
            Integer submissionStatus = gen2Survey.getDatstatSubmissionstatus();

            Long studyActivityId = jdbiActivity.findIdByStudyIdAndCode(studyId, activityCode).get();
            LOG.info("study activity ID: " + studyActivityId);
            List<ActivityVersionDto> versionDto = jdbiActivityVersion.findAllVersionsInAscendingOrder(studyActivityId);
            //get version/revision with lowest Timestamp
            Long revisionStart = null;
            for (ActivityVersionDto dto : versionDto) {
                if (revisionStart == null) {
                    revisionStart = dto.getRevStart();
                } else {
                    if (dto.getRevStart() < revisionStart) {
                        revisionStart = dto.getRevStart();
                    }
                }
            }
            Long statusLastUpdatedAt = null;
            Long ddpCreatedAt = null;
            Long ddpCompletedAt = null;

            if (ddpCreated != null) {
                Instant instant;
                try {
                    instant = Instant.parse(ddpCreated);
                } catch (DateTimeParseException e) {
                    throw new Exception("Could not parse required createdAt value for " + activityCode + " survey, value is " + ddpCreated);
                }
                ddpCreatedAt = instant.toEpochMilli();
            } else {
                throw new Exception("Missing required createdAt value for " + activityCode + " survey");
            }

            if (ddpLastUpdated != null) {
                Instant instant;
                try {
                    instant = Instant.parse(ddpLastUpdated);
                } catch (DateTimeParseException e) {
                    throw new Exception("Could not parse required lastUpdated value for " + activityCode
                            + " survey, value is " + ddpLastUpdated);
                }
                statusLastUpdatedAt = instant.toEpochMilli();
            } else {
                throw new Exception("Missing required lastUpdated value for " + activityCode + " survey");
            }

            if (ddpCompleted != null) {
                Instant instant;
                try {
                    instant = Instant.parse(ddpCompleted);
                } catch (DateTimeParseException e) {
                    throw new Exception("Could not parse required completedAt value for " + activityCode
                            + " survey, value is " + ddpCompleted);
                }
                ddpCompletedAt = instant.toEpochMilli();
            }

            InstanceStatusType instanceCurrentStatus =
                    getActivityInstanceStatus(submissionStatus, ddpCreatedAt, statusLastUpdatedAt, ddpCompletedAt);

            // Read only is always undefined for things that aren't consent- we rely on the user being terminated to show r/o activities
            Boolean isReadonly = null;
            if (gen2Survey instanceof ConsentSurvey && instanceCurrentStatus == InstanceStatusType.COMPLETE) {
                isReadonly = true;
            }
            // Read only is always undefined - we rely on the user being terminated to show read only activities
            String instanceGuid = activityInstanceDao
                    .insertInstance(studyActivityId, participantGuid, participantGuid, InstanceStatusType.CREATED,
                            isReadonly, ddpCreatedAt,
                            submissionId.longValue(), sessionId, surveyVersion).getGuid();

            LOG.info("Created activity instance {} for activity {} and user {}",
                    instanceGuid, activityCode, participantGuid);

            long activityInstanceId = jdbiActivityInstance.getActivityInstanceId(instanceGuid);

            if (InstanceStatusType.IN_PROGRESS == instanceCurrentStatus) {
                activityInstanceStatusDao
                        .insertStatus(activityInstanceId, InstanceStatusType.IN_PROGRESS, statusLastUpdatedAt, participantGuid);
            } else if (InstanceStatusType.COMPLETE == instanceCurrentStatus) {
                activityInstanceStatusDao.insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, ddpCompletedAt, participantGuid);
                if (statusLastUpdatedAt > ddpCompletedAt) {
                    activityInstanceStatusDao
                            .insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, statusLastUpdatedAt, participantGuid);
                }
            }

            return instanceGuid;
        } else {
            // This is ok, when we try to fill in survey later, it will also be null and we will not do it.
            return null;
        }
    }

    void loadAboutYouSurveyData(Handle handle,
                                ParticipantData participantData,
                                String participantGuid,
                                String instanceGuid,
                                AnswerDao answerDao) throws Exception {

        AboutYouSurvey aboutYouSurvey = null;
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
        if (surveyData != null) {
            //LOG.info("survey data loaded..");
            aboutYouSurvey = surveyData.getAboutYouSurvey();
        }
        if (aboutYouSurvey == null) {
            LOG.warn("NO About You Survey !");
            return;
            //watch out.. early return;
        }

        LOG.info("Populating AboutYou Survey...");

        // Date of Diagnosis Picklist Month/Year
        DateValue diagnosisDate = new DateValue(aboutYouSurvey.getDiagnosisDateYear(),
                aboutYouSurvey.getDiagnosisDateMonth(),
                null);
        answerDateQuestion(handle,
                "DIAGNOSIS_DATE", participantGuid, instanceGuid, diagnosisDate, answerDao);

        // Source is HEADNECK not HEADFACENECK
        List<SelectedPicklistOption> diagnosisPrimaryLoc = getDiagnosisLoc("DiagnosisPrimaryLoc",
                aboutYouSurvey,
                true,
                false, false);
        answerPickListQuestion(handle,
                "DIAGNOSIS_PRIMARY_LOC", participantGuid, instanceGuid, diagnosisPrimaryLoc, answerDao);

        List<SelectedPicklistOption> diagnosisSpread = answerYesNoDk("DiagnosisSpread", aboutYouSurvey);
        answerPickListQuestion(handle,
                "DIAGNOSIS_SPREAD", participantGuid, instanceGuid, diagnosisSpread, answerDao);

        // Source is HEADNECK not HEADFACENECK
        List<SelectedPicklistOption> diagnosisSpreadLoc = getDiagnosisLoc("DiagnosisSpreadLoc",
                aboutYouSurvey,
                true,
                false, false);
        answerPickListQuestion(handle,
                "DIAGNOSIS_SPREAD_LOC", participantGuid, instanceGuid, diagnosisSpreadLoc, answerDao);

        List<SelectedPicklistOption> postDiagnosisSpread = answerYesNoDk("PostDiagnosisSpread", aboutYouSurvey);
        answerPickListQuestion(handle, "POST_DIAGNOSIS_SPREAD", participantGuid, instanceGuid,
                postDiagnosisSpread, answerDao);

        // Source is HEADNECK
        List<SelectedPicklistOption> postDiagnosisSpreadLoc = getDiagnosisLoc("PostDiagnosisSpreadLoc",
                aboutYouSurvey,
                true,
                false, false);
        answerPickListQuestion(handle, "POST_DIAGNOSIS_SPREAD_LOC", participantGuid, instanceGuid,
                postDiagnosisSpreadLoc, answerDao);

        List<SelectedPicklistOption> localRecurrence = answerYesNoDk("LocalRecurrence", aboutYouSurvey);
        answerPickListQuestion(handle, "LOCAL_RECURRENCE", participantGuid, instanceGuid,
                localRecurrence, answerDao);

        // Source is HEADNECK
        List<SelectedPicklistOption> localRecurrenceLoc = getDiagnosisLoc("LocalRecurrenceLoc",
                aboutYouSurvey,
                true,
                false, false);
        answerPickListQuestion(handle, "LOCAL_RECURRENCE_LOC", participantGuid, instanceGuid,
                localRecurrenceLoc, answerDao);

        List<SelectedPicklistOption> diagnosisLoc = getDiagnosisLoc("DiagnosisLoc", aboutYouSurvey,
                false,
                true, false);
        answerPickListQuestion(handle,
                "DIAGNOSIS_LOC", participantGuid, instanceGuid, diagnosisLoc, answerDao);

        List<SelectedPicklistOption> everLocation = getDiagnosisLoc("EverLocation", aboutYouSurvey,
                false,
                true, false);
        answerPickListQuestion(handle,
                "EVER_LOCATION", participantGuid, instanceGuid, everLocation, answerDao);

        List<SelectedPicklistOption> currentLocation = getDiagnosisLoc("CurrentLocation", aboutYouSurvey,
                false,
                true,
                true);
        answerPickListQuestion(handle,
                "CURRENT_LOCATION", participantGuid, instanceGuid, currentLocation, answerDao);

        List<SelectedPicklistOption> surgery = answerYesNoDk("Surgery", aboutYouSurvey);
        answerPickListQuestion(handle,
                "SURGERY", participantGuid, instanceGuid, surgery, answerDao);

        List<SelectedPicklistOption> surgeryCleanMargins = answerYesNoDk("SurgeryCleanMargins", aboutYouSurvey);
        answerPickListQuestion(handle,
                "SURGERY_CLEAN_MARGINS", participantGuid, instanceGuid, surgeryCleanMargins, answerDao);

        List<SelectedPicklistOption> radiation = answerYesNoDk("Radiation", aboutYouSurvey);
        answerPickListQuestion(handle,
                "RADIATION", participantGuid, instanceGuid, radiation, answerDao);

        List<SelectedPicklistOption> radiationSurgery = getRadiationSurgery(aboutYouSurvey);
        answerPickListQuestion(handle,
                "RADIATION_SURGERY", participantGuid, instanceGuid, radiationSurgery, answerDao);

        answerTextQuestion(handle,
                "TREATMENT_PAST", participantGuid, instanceGuid, aboutYouSurvey.getTreatmentPast(), answerDao);

        answerTextQuestion(handle,
                "TREATMENT_NOW", participantGuid, instanceGuid, aboutYouSurvey.getTreatmentNow(), answerDao);

        answerTextQuestion(handle,
                "ALL_TREATMENTS", participantGuid, instanceGuid, aboutYouSurvey.getAllTreatment(), answerDao);

        List<SelectedPicklistOption> currentlyTreated = answerYesNoDk("CurrentlyTreated", aboutYouSurvey);
        answerPickListQuestion(handle, "CURRENTLY_TREATED", participantGuid, instanceGuid, currentlyTreated, answerDao);

        answerTextQuestion(handle,
                "CURRENT_THERAPIES", participantGuid, instanceGuid, aboutYouSurvey.getCurrentTherapy(), answerDao);

        List<SelectedPicklistOption> otherCancer = answerYesNoDk("OtherCancer", aboutYouSurvey);
        answerPickListQuestion(handle, "OTHER_CANCER", participantGuid, instanceGuid, otherCancer, answerDao);

        List<SelectedPicklistOption> otherCancerRadiaton = answerYesNoDk("OtherCancerRadiation", aboutYouSurvey);
        answerPickListQuestion(handle, "OTHER_CANCER_RADIATION", participantGuid, instanceGuid, otherCancerRadiaton, answerDao);

        answerTextQuestion(handle,
                "OTHER_CANCER_RADIATION_LOC", participantGuid, instanceGuid, aboutYouSurvey.getOtherCancerRadiationLoc(), answerDao);

        List<SelectedPicklistOption> diseaseFreeNow = answerYesNoDk("DiseaseFreeNow", aboutYouSurvey);
        answerPickListQuestion(handle, "DISEASE_FREE_NOW", participantGuid, instanceGuid, diseaseFreeNow, answerDao);

        List<SelectedPicklistOption> supportMembership = answerYesNoDk("SupportMembership", aboutYouSurvey);
        answerPickListQuestion(handle, "SUPPORT_MEMBERSHIP", participantGuid, instanceGuid, supportMembership, answerDao);

        answerTextQuestion(handle,
                "SUPPORT_MEMBERSHIP_TEXT", participantGuid, instanceGuid, aboutYouSurvey.getSupportMembershipText(), answerDao);

        answerTextQuestion(handle,
                "EXPERIENCE", participantGuid, instanceGuid, aboutYouSurvey.getExperienceText(), answerDao);

        List<SelectedPicklistOption> hispanic = answerYesNoDk("Hispanic", aboutYouSurvey);
        answerPickListQuestion(handle, "HISPANIC", participantGuid, instanceGuid, hispanic, answerDao);

        List<SelectedPicklistOption> race = getRace(aboutYouSurvey);
        answerPickListQuestion(handle, "RACE", participantGuid, instanceGuid, race, answerDao);

        answerTextQuestion(handle,
                "REFERRAL_SOURCE", participantGuid, instanceGuid, aboutYouSurvey.getReferralSource(), answerDao);

        DateValue birthYear = new DateValue(aboutYouSurvey.getBirthYear(),
                null,
                null);
        answerDateQuestion(handle,
                "BIRTH_YEAR", participantGuid, instanceGuid, birthYear, answerDao);

        SelectedPicklistOption country = getCountry(aboutYouSurvey);
        if (country != null) {
            answerPickListQuestion(handle, "COUNTRY", participantGuid, instanceGuid, Collections.singletonList(country), answerDao);
        }

        answerTextQuestion(handle,
                "POSTAL_CODE", participantGuid, instanceGuid, aboutYouSurvey.getPostalCode(), answerDao);

        setOtherCancers(handle, participantGuid, instanceGuid, aboutYouSurvey, answerDao);
    }

    private InstanceStatusType getActivityInstanceStatus(Integer submissionStatusCode, Long createdAt,
                                                         Long lastUpdatedAt, Long completedAt) throws Exception {
        //If DDP_CREATED == DDP_LASTUPDATED then started is just CREATED
        //If DDP_CREATED < DDP_LASTUPDATED then started is IN_PROGRESS

        // Completed = 1
        // In Progress = 2
        // Terminated = 5
        if (completedAt != null) {
            if (submissionStatusCode != 1 && submissionStatusCode != 5) {
                throw new Exception("The survey has a completedAt value, but claims to not be completed!");
            }
            return InstanceStatusType.COMPLETE;
        } else if (!createdAt.equals(lastUpdatedAt)) {
            if (submissionStatusCode != 2 && submissionStatusCode != 5) {
                throw new Exception("The survey has a different createdAt/lastUpdatedAt values, but claims to not be in progress!");
            }
            return InstanceStatusType.IN_PROGRESS;
        } else {
            if (submissionStatusCode != 2 && submissionStatusCode != 5) {
                throw new Exception("The survey has the same createdAt/lastUpdatedAt values, but claims to not be in progress!");
            }
            return InstanceStatusType.CREATED;
        }
    }

    public void loadReleaseSurveyData(Handle handle, ParticipantData participantData,
                                      String userGuid, String studyGuid,
                                      String instanceGuid,
                                      long studyId,
                                      long userId,
                                      JdbiUserLegacyInfo jdbiUserLegacyInfo,
                                      MedicalProviderDao medicalProviderDao,
                                      JdbiUserStudyEnrollment jdbiUserStudyEnrollment,
                                      AnswerDao answerDao) throws Exception {

        LOG.info("Populating Release Survey...");
        ReleaseSurvey releaseSurvey = null;
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
        if (surveyData != null) {
            releaseSurvey = surveyData.getReleaseSurvey();
        }
        if (releaseSurvey == null) {
            LOG.warn("NO Release Survey !");
            return;
            //watch out.. early return;
        }

        String ddpCompleted = releaseSurvey.getDdpFirstcompleted();
        String ddpCreated = releaseSurvey.getDdpCreated();
        String ddpLastUpdated = releaseSurvey.getDdpLastupdated();
        Integer submissionStatus = releaseSurvey.getDatstatSubmissionstatus();

        Long statusLastUpdatedAt = null;
        Long ddpCreatedAt = null;
        Long ddpCompletedAt = null;
        InstanceStatusType instanceCurrentStatus = null;
        if (ddpCreated != null) {
            Instant instant;
            try {
                instant = Instant.parse(ddpCreated);
            } catch (DateTimeParseException e) {
                throw new Exception("Could not parse required createdAt value for release survey, value is " + ddpCreated);
            }
            ddpCreatedAt = instant.toEpochMilli();
        } else {
            throw new Exception("Missing required createdAt value for release survey");
        }

        if (ddpLastUpdated != null) {
            Instant instant;
            try {
                instant = Instant.parse(ddpLastUpdated);
            } catch (DateTimeParseException e) {
                throw new Exception("Could not parse required lastUpdated value for release survey, value is " + ddpLastUpdated);
            }
            statusLastUpdatedAt = instant.toEpochMilli();
        } else {
            throw new Exception("Missing required lastUpdated value for release survey");
        }

        if (ddpCompleted != null) {
            Instant instant;
            try {
                instant = Instant.parse(ddpCompleted);
            } catch (DateTimeParseException e) {
                throw new Exception("Could not parse required completedAt value for release survey, value is " + ddpCompleted);
            }
            ddpCompletedAt = instant.toEpochMilli();
        }

        //Load legacy address fields to user_legacy_info
        String firstName = releaseSurvey.getDatstatFirstname();
        String lastName = releaseSurvey.getDatstatLastname();
        String street1 = releaseSurvey.getStreet1();
        String street2 = releaseSurvey.getStreet2();
        String city = releaseSurvey.getCity();
        String state = releaseSurvey.getState();
        String country = releaseSurvey.getCountry();
        String postalCode = releaseSurvey.getPostalCode();
        String phone = releaseSurvey.getPhoneNumber();
        String fullName = firstName.trim().concat(" ").concat(lastName.trim());


        SurveyAddress userLegacyInfo = new SurveyAddress(fullName, street1, street2,
                city, state, country, postalCode, phone);

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        String releaseSurveyAddressJsonStr = gson.toJson(userLegacyInfo);

        jdbiUserLegacyInfo.insertUserLegacyInfo(userId, releaseSurveyAddressJsonStr);

        //load Physician list
        List<Physician> physicianList = releaseSurvey.getPhysicianList();
        if (physicianList != null) {
            for (Physician physician : physicianList) {

                String guid = getMedicalProviderGuid(handle);

                medicalProviderDao.insert(new MedicalProviderDto(
                        null,
                        guid,
                        userId,
                        studyId,
                        InstitutionType.PHYSICIAN,
                        physician.getInstitution(),
                        physician.getName(),
                        physician.getCity(),
                        physician.getState(),
                        physician.getZipcode(),
                        physician.getPhonenumber(),
                        physician.getPhysicianid(),
                        physician.getStreetaddress()
                ));
            }
        }

        //load Institutions

        List<Institution> institutionList = releaseSurvey.getInstitutions();
        if (institutionList != null) {
            for (Institution institution : institutionList) {

                String guid = getMedicalProviderGuid(handle);
                medicalProviderDao.insert(new MedicalProviderDto(
                        null,
                        guid,
                        userId,
                        studyId,
                        InstitutionType.INSTITUTION,
                        institution.getInstitution(),
                        null, //name
                        institution.getCity(),
                        institution.getState(),
                        null,
                        null,
                        institution.getInstitutionId(),
                        null
                ));
            }
        }

        if (releaseSurvey.getInitialBiopsyInstitution() != null
                || releaseSurvey.getInitialBiopsyCity() != null
                || releaseSurvey.getInitialBiopsyState() != null) {
            String guid = getMedicalProviderGuid(handle);
            medicalProviderDao.insert(new MedicalProviderDto(
                    null,
                    guid,
                    userId,
                    studyId,
                    InstitutionType.INITIAL_BIOPSY,
                    releaseSurvey.getInitialBiopsyInstitution(),
                    null, //name
                    releaseSurvey.getInitialBiopsyCity(),
                    releaseSurvey.getInitialBiopsyState(),
                    null,
                    null,
                    DEFAULT_DSM_BIOPSY_GUID, null
            ));
        }

        if (releaseSurvey.getAgreementAgree() != null) {
            answerAgreementQuestion(handle,
                    "RELEASE_AGREEMENT",
                    userGuid,
                    instanceGuid,
                    getBooleanValue(releaseSurvey.getAgreementAgree()),
                    answerDao);
        }


        instanceCurrentStatus = getActivityInstanceStatus(submissionStatus, ddpCreatedAt, statusLastUpdatedAt, ddpCompletedAt);

        if (instanceCurrentStatus == InstanceStatusType.COMPLETE) {
            if (ddpCompletedAt == 0) {
                jdbiUserStudyEnrollment.changeUserStudyEnrollmentStatus(userGuid, studyGuid,
                        EnrollmentStatusType.ENROLLED, statusLastUpdatedAt);
            } else {
                jdbiUserStudyEnrollment.changeUserStudyEnrollmentStatus(userGuid, studyGuid,
                        EnrollmentStatusType.ENROLLED, ddpCompletedAt);
            }
        }
    }

    String getMedicalProviderGuid(Handle handle) {
        return DBUtils.uniqueStandardGuid(handle,
                MedicalProviderTable.TABLE_NAME, MedicalProviderTable.MEDICAL_PROVIDER_GUID);
    }

    public void loadConsentSurveyData(Handle handle,
                                      ParticipantData participantData,
                                      String participantGuid,
                                      String instanceGuid,
                                      AnswerDao answerDao) throws Exception {

        LOG.info("Populating Consent Survey...");
        ConsentSurvey consentSurvey = null;
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
        if (surveyData != null) {
            consentSurvey = surveyData.getConsentSurvey();
        }
        if (consentSurvey == null) {
            LOG.warn("NO Consent Survey !");
            return;
        }

        answerBooleanQuestion(handle, "CONSENT_BLOOD",
                participantGuid, instanceGuid, consentSurvey.getConsentBlood(), answerDao);

        answerBooleanQuestion(handle, "CONSENT_TISSUE",
                participantGuid, instanceGuid, consentSurvey.getConsentTissue(), answerDao);

        answerTextQuestion(handle, "TREATMENT_NOW_TEXT",
                participantGuid, instanceGuid, consentSurvey.getTreatmentNowText(), answerDao);

        answerTextQuestion(handle, "TREATMENT_PAST_TEXT",
                participantGuid, instanceGuid, consentSurvey.getTreatmentPastText(), answerDao);

        DateValue treatmentStartDate = new DateValue(
                consentSurvey.getTreatmentNowStartYear(),
                consentSurvey.getTreatmentNowStartMonth(),
                null);
        answerDateQuestion(handle, "TREATMENT_NOW_START",
                participantGuid, instanceGuid, treatmentStartDate, answerDao);

        answerTextQuestion(handle, "CONSENT_FULLNAME",
                participantGuid, instanceGuid, consentSurvey.getFullname(), answerDao);

        if (StringUtils.isNotBlank(consentSurvey.getConsentDob())) {
            DateValue value = parseIso860Date(consentSurvey.getConsentDob());
            answerDateQuestion(handle, "CONSENT_DOB", participantGuid, instanceGuid, value, answerDao);
        }
    }

    private DateValue parseIso860Date(String dateValue) throws ParseException {
        SimpleDateFormat iso860 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date consentDOB = iso860.parse(dateValue);
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(consentDOB);
        return new DateValue(gregorianCalendar.get(Calendar.YEAR),
                gregorianCalendar.get(Calendar.MONTH) + 1, // GregorianCalendar months are 0 indexed
                gregorianCalendar.get(Calendar.DAY_OF_MONTH));
    }

    public void loadFollowupConsentSurveyData(Handle handle, ParticipantData participantData,
                                              String participantGuid, String instanceGuid,
                                              AnswerDao answerDao, long userId) throws Exception {

        LOG.info("Populating Followup Consent Survey...");
        FollowupConsentSurvey followupConsentSurvey = null;
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
        if (surveyData != null) {
            followupConsentSurvey = surveyData.getFollowupConsentSurvey();
        }
        if (followupConsentSurvey == null) {
            LOG.warn("NO Followup Consent Survey !");
            return;
            //watch out.. early return;
        }

        answerBooleanQuestion(handle, "FOLLOWUPCONSENT_BLOOD",
                participantGuid, instanceGuid, followupConsentSurvey.getConsentBlood(), answerDao);

        answerBooleanQuestion(handle, "FOLLOWUPCONSENT_TISSUE",
                participantGuid, instanceGuid, followupConsentSurvey.getConsentTissue(), answerDao);

        answerTextQuestion(handle, "FOLLOWUPCONSENT_FULLNAME",
                participantGuid, instanceGuid, followupConsentSurvey.getFullname(), answerDao);

        if (StringUtils.isNotBlank(followupConsentSurvey.getConsentDob())) {
            DateValue value = parseIso860Date(followupConsentSurvey.getConsentDob());
            answerDateQuestion(handle, "FOLLOWUPCONSENT_DOB", participantGuid, instanceGuid, value, answerDao);
        }

        updateFollowUpConsentTriggerId(handle, userId);
    }

    void updateFollowUpConsentTriggerId(Handle handle, long userId) {
        handle.createUpdate("update activity_instance set ondemand_trigger_id = :triggerId "
                + " where participant_id = :participantId")
                .bind("triggerId", DSM_DEFAULT_ON_DEMAND_TRIGGER_ID)
                .bind("participantId", userId)
                .execute();
    }

    public void loadLovedOneSurveyData(Handle handle, ParticipantData participantData,
                                       String participantGuid,
                                       String instanceGuid,
                                       AnswerDao answerDao) throws Exception {

        LOG.info("Populating LovedOne Survey...");
        LovedOneSurvey lovedOneSurvey = null;
        DatstatSurveyData surveyData = participantData.getParticipantUser().getDatstatsurveydata();
        if (surveyData != null) {
            lovedOneSurvey = surveyData.getLovedOneSurvey();
        }
        if (lovedOneSurvey == null) {
            LOG.warn("NO LovedOne Survey !");
            return;
            //watch out.. early return;
        }

        // RelationTo
        LovedOneRelationTo lovedOneRelationTo = LovedOneRelationTo.fromDatStatEnum(lovedOneSurvey.getRelationTo());
        if (lovedOneRelationTo != null) {
            if (lovedOneRelationTo == LovedOneRelationTo.OTHER) {
                answerPickListQuestion(handle, "LOVEDONE_RELATION_TO", participantGuid, instanceGuid,
                        Collections.singletonList(new SelectedPicklistOption(lovedOneRelationTo.name(),
                                lovedOneSurvey.getRelationToOtherText())), answerDao);
            } else {
                answerPickListQuestion(handle, "LOVEDONE_RELATION_TO", participantGuid, instanceGuid,
                        Collections.singletonList(new SelectedPicklistOption(lovedOneRelationTo.name())), answerDao);
            }
        }

        // First Name, Last Name
        answerTextQuestion(handle, "LOVEDONE_FIRST_NAME", participantGuid, instanceGuid, lovedOneSurvey.getFirstName(), answerDao);
        answerTextQuestion(handle, "LOVEDONE_LAST_NAME", participantGuid, instanceGuid, lovedOneSurvey.getLastName(), answerDao);

        answerTextQuestion(handle, "LOVEDONE_DIAGNOSIS_POSTAL_CODE", participantGuid, instanceGuid,
                lovedOneSurvey.getDiagnosisPostalCode(), answerDao);
        answerTextQuestion(handle, "LOVEDONE_PASSED_POSTAL_CODE", participantGuid, instanceGuid,
                lovedOneSurvey.getPassedPostalCode(), answerDao);

        answerTextQuestion(handle, "LOVEDONE_OTHER_CANCER_TEXT", participantGuid, instanceGuid,
                lovedOneSurvey.getOtherCancerText(), answerDao);

        // DOB
        if (StringUtils.isNotBlank(lovedOneSurvey.getDob())) {
            LocalDateTime dobActual = null;
            if (lovedOneSurvey.getDob().contains("T")) {
                dobActual = LocalDateTime.parse(lovedOneSurvey.getDob());
            } else {
                LocalDate localDob = LocalDate.parse(lovedOneSurvey.getDob(), DateTimeFormatter.ofPattern(DOB_DATE_FORMAT));
                dobActual = LocalDateTime.from(localDob.atStartOfDay());
            }

            answerDateQuestion(handle, "LOVEDONE_DOB", participantGuid, instanceGuid, dobActual, answerDao);
        }

        // Date of Diagnosis Month/Year
        DateValue diagnosisDate = new DateValue(lovedOneSurvey.getDiagnosisDateYear(),
                lovedOneSurvey.getDiagnosisDateMonth(),
                null);
        answerDateQuestion(handle,
                "LOVEDONE_DIAGNOSIS_DATE", participantGuid, instanceGuid, diagnosisDate, answerDao);

        // Passing Date Month/Year
        DateValue passingDate = new DateValue(lovedOneSurvey.getPassingDateYear(),
                lovedOneSurvey.getPassingDateMonth(),
                null);
        answerDateQuestion(handle,
                "LOVEDONE_PASSING_DATE", participantGuid, instanceGuid, passingDate, answerDao);

        List<SelectedPicklistOption> diagnosesPrimaryLoc = getDiagnosisLoc("DiagnosisPrimaryLoc",
                lovedOneSurvey,
                false,
                true,
                false);
        if (!diagnosesPrimaryLoc.isEmpty()) {
            answerPickListQuestion(handle, "LOVEDONE_DIAGNOSIS_PRIMARY_LOC", participantGuid,
                    instanceGuid, diagnosesPrimaryLoc, answerDao);
        }

        List<SelectedPicklistOption> diagnosesSpreadLoc = getDiagnosisLoc("DiagnosisSpreadLoc",
                lovedOneSurvey,
                false,
                true,
                false);
        if (!diagnosesSpreadLoc.isEmpty()) {
            answerPickListQuestion(handle, "LOVEDONE_DIAGNOSIS_SPREAD_LOC", participantGuid,
                    instanceGuid, diagnosesSpreadLoc, answerDao);
        }

        List<SelectedPicklistOption> otherCancers = answerYesNoDk("OtherCancer", lovedOneSurvey);
        if (!otherCancers.isEmpty()) {
            answerPickListQuestion(handle, "LOVEDONE_OTHER_CANCER", participantGuid,
                    instanceGuid, otherCancers, answerDao);
        }

        List<SelectedPicklistOption> otherCancersRadiation = answerYesNoDk("OtherCancerRadiation",
                lovedOneSurvey, lovedOneSurvey.getOtherCancerRadiationLoc());
        if (!otherCancersRadiation.isEmpty()) {
            answerPickListQuestion(handle, "LOVEDONE_OTHER_CANCER_RADIATION", participantGuid,
                    instanceGuid, otherCancersRadiation, answerDao);
        }

        answerTextQuestion(handle, "LOVEDONE_EXPERIENCE", participantGuid, instanceGuid,
                lovedOneSurvey.getExperienceText(), answerDao);

        List<SelectedPicklistOption> futureContact = answerYesNoDk("FutureContact", lovedOneSurvey);
        if (!futureContact.isEmpty()) {
            answerPickListQuestion(handle, "LOVEDONE_FUTURE_CONTACT", participantGuid,
                    instanceGuid, futureContact, answerDao);
        }
    }

    private List<SelectedPicklistOption> answerYesNoDk(String sourceFunctionName, Object survey)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        return answerYesNoDk(sourceFunctionName, survey, null);
    }

    private List<SelectedPicklistOption> answerYesNoDk(String sourceFunctionName, Object survey, String detailText)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class surveyClass = survey.getClass();
        Method yesMethod = surveyClass.getMethod("get" + sourceFunctionName + "Yes");
        Method noMethod = surveyClass.getMethod("get" + sourceFunctionName + "No");
        Method dkMethod = surveyClass.getMethod("get" + sourceFunctionName + "Dk");
        List<SelectedPicklistOption> picklistOptions = new ArrayList<>();

        if (yesMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(YES, detailText));
        }
        if (noMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(NO, detailText));
        }
        if (dkMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(DK, detailText));
        }

        return picklistOptions;
    }

    private List<SelectedPicklistOption> getRadiationSurgery(AboutYouSurvey aboutYouSurvey) {
        List<SelectedPicklistOption> picklistOptions = new ArrayList<>();
        if (aboutYouSurvey.getRadiationSurgeryBefore() == 1) {
            picklistOptions.add(new SelectedPicklistOption(BEFORE));
        }
        if (aboutYouSurvey.getRadiationSurgeryAfter() == 1) {
            picklistOptions.add(new SelectedPicklistOption(AFTER));
        }
        if (aboutYouSurvey.getRadiationSurgeryBoth() == 1) {
            picklistOptions.add(new SelectedPicklistOption(BOTH));
        }
        if (aboutYouSurvey.getRadiationSurgeryDk() == 1) {
            picklistOptions.add(new SelectedPicklistOption(DK));
        }
        return picklistOptions;
    }

    private List<SelectedPicklistOption> getRace(AboutYouSurvey aboutYouSurvey) {
        List<SelectedPicklistOption> picklistOptions = new ArrayList<>();
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceAmericanIndian())) {
            picklistOptions.add(new SelectedPicklistOption("AMERICAN_INDIAN"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceJapanese())) {
            picklistOptions.add(new SelectedPicklistOption("JAPANESE"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceChinese())) {
            picklistOptions.add(new SelectedPicklistOption("CHINESE"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceOtherEastAsian())) {
            picklistOptions.add(new SelectedPicklistOption("OTHER_EAST_ASIAN"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceSouthEastAsian())) {
            picklistOptions.add(new SelectedPicklistOption("SOUTH_EAST_ASIAN"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceBlack())) {
            picklistOptions.add(new SelectedPicklistOption("BLACK"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceNativeHawaiian())) {
            picklistOptions.add(new SelectedPicklistOption("NATIVE_HAWAIIAN"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceWhite())) {
            picklistOptions.add(new SelectedPicklistOption("WHITE"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceNoAnswer())) {
            picklistOptions.add(new SelectedPicklistOption("NO_ANSWER"));
        }
        if (OPTION_SELECTED.equals(aboutYouSurvey.getRaceOther())) {
            String extra = aboutYouSurvey.getRaceOtherOtherText();
            picklistOptions.add(new SelectedPicklistOption(OTHER, extra));
        }
        return picklistOptions;
    }

    String setOtherCancers(Handle handle, String participantGuid, String instanceGuid, AboutYouSurvey aboutYouSurvey, AnswerDao answerDao) {
        CompositeAnswer compositeAnswer = new CompositeAnswer(null, "OTHER_CANCER_LIST", null);
        if (aboutYouSurvey.getOtherCancerList() != null) {
            aboutYouSurvey.getOtherCancerList().stream()
                    .forEach(otherCancer -> {
                        List<Answer> innerAnswer = new ArrayList<>();
                        innerAnswer.add(new TextAnswer(null, "OTHER_CANCER_LIST_NAME", null, otherCancer.getDiseasename()));
                        DateValue otherCancerYear = new DateValue(otherCancer.getDiagnosisyear(),
                                null,
                                null);
                        innerAnswer.add(new DateAnswer(null, "OTHER_CANCER_LIST_YEAR", null, otherCancerYear));
                        compositeAnswer.addRowOfChildAnswers(innerAnswer);
                    });

            return answerDao.createAnswer(participantGuid, instanceGuid, compositeAnswer).getAnswerGuid();
        }
        return null;
    }

    private List<SelectedPicklistOption> getDiagnosisLoc(String sourceFunctionName,
                                                         Object survey,
                                                         boolean headNeck,
                                                         boolean withDk,
                                                         boolean withNed)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class surveyClass = survey.getClass();
        Method headneckMethod;
        if (headNeck) {
            headneckMethod = surveyClass.getMethod("get" + sourceFunctionName + "Headneck");
        } else {
            headneckMethod = surveyClass.getMethod("get" + sourceFunctionName + "Headfaceneck");
        }
        Method scalpMethod = surveyClass.getMethod("get" + sourceFunctionName + "Scalp");
        Method breastMethod = surveyClass.getMethod("get" + sourceFunctionName + "Breast");
        Method heartMethod = surveyClass.getMethod("get" + sourceFunctionName + "Heart");
        Method liverMethod = surveyClass.getMethod("get" + sourceFunctionName + "Liver");
        Method spleenMethod = surveyClass.getMethod("get" + sourceFunctionName + "Spleen");
        Method lungMethod = surveyClass.getMethod("get" + sourceFunctionName + "Lung");
        Method brainMethod = surveyClass.getMethod("get" + sourceFunctionName + "Brain");
        Method lymphMethod = surveyClass.getMethod("get" + sourceFunctionName + "Lymph");
        Method bonelimbMethod = surveyClass.getMethod("get" + sourceFunctionName + "Bonelimb");
        Method bonelimbExtraMethod = surveyClass.getMethod("get" + sourceFunctionName + "BonelimbBonelimbText");
        Method abdominalMethod = surveyClass.getMethod("get" + sourceFunctionName + "Abdominal");
        Method abdominalExtraMethod = surveyClass.getMethod("get" + sourceFunctionName + "AbdominalAbdominalText");
        Method otherMethod = surveyClass.getMethod("get" + sourceFunctionName + "Other");
        Method otherExtraMethod = surveyClass.getMethod("get" + sourceFunctionName + "OtherOtherText");

        List<SelectedPicklistOption> picklistOptions = new ArrayList<>();
        if (headneckMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(HEADFACENECK));
        }
        if (scalpMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(SCALP));
        }
        if (breastMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(BREAST));
        }
        if (heartMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(HEART));
        }
        if (liverMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(LIVER));
        }
        if (spleenMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(SPLEEN));
        }
        if (lungMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(LUNG));
        }
        if (brainMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(BRAIN));
        }
        if (lymphMethod.invoke(survey) == OPTION_SELECTED) {
            picklistOptions.add(new SelectedPicklistOption(LYMPH));
        }
        if (bonelimbMethod.invoke(survey) == OPTION_SELECTED) {
            String extra = (String) bonelimbExtraMethod.invoke(survey);
            picklistOptions.add(new SelectedPicklistOption(BONELIMB, extra));
        }
        if (abdominalMethod.invoke(survey) == OPTION_SELECTED) {
            String extra = (String) abdominalExtraMethod.invoke(survey);
            picklistOptions.add(new SelectedPicklistOption(ABDOMINAL, extra));
        }
        if (otherMethod.invoke(survey) == OPTION_SELECTED) {
            String extra = (String) otherExtraMethod.invoke(survey);
            picklistOptions.add(new SelectedPicklistOption(OTHER, extra));
        }
        if (withDk) {
            Method dkMethod = surveyClass.getMethod("get" + sourceFunctionName + "Dk");
            if (dkMethod.invoke(survey) == OPTION_SELECTED) {
                picklistOptions.add(new SelectedPicklistOption(DK));
            }
        }
        if (withNed) {
            Method dkMethod = surveyClass.getMethod("get" + sourceFunctionName + "Ned");
            if (dkMethod.invoke(survey) == OPTION_SELECTED) {
                picklistOptions.add(new SelectedPicklistOption(NED));
            }
        }

        return picklistOptions;
    }

    public String answerDateQuestion(Handle handle, String pepperQuestionStableId, String participantGuid, String instanceGuid,
                                     LocalDateTime value, AnswerDao answerDao) {

        LocalDate localDate = null;
        if (value != null) {
            localDate = LocalDate.of(value.getYear(), value.getMonthValue(), value.getDayOfMonth());
        }
        return answerDateQuestion(handle, pepperQuestionStableId, participantGuid, instanceGuid, localDate, answerDao);
    }

    public String answerDateQuestion(Handle handle, String pepperQuestionStableId, String participantGuid, String instanceGuid,
                                     DateValue value, AnswerDao answerDao) {
        Answer answer = new DateAnswer(null, pepperQuestionStableId, null, null, null, null);
        if (value != null) {
            answer = new DateAnswer(null, pepperQuestionStableId, null,
                    value.getYear(),
                    value.getMonth(),
                    value.getDay());
        }
        return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
    }

    public String answerDateQuestion(Handle handle, String pepperQuestionStableId, String participantGuid, String instanceGuid,
                                     LocalDate value, AnswerDao answerDao) {

        Answer answer = new DateAnswer(null, pepperQuestionStableId, null, null, null, null);
        if (value != null) {
            answer = new DateAnswer(null, pepperQuestionStableId, null,
                    value.getYear(),
                    value.getMonthValue(),
                    value.getDayOfMonth());
        }
        return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
    }

    public String answerTextQuestion(Handle handle,
                                     String pepperQuestionStableId,
                                     String participantGuid,
                                     String instanceGuid,
                                     String value, AnswerDao answerDao) {
        if (value != null) {
            Answer answer = new TextAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public String answerBooleanQuestion(Handle handle,
                                        String pepperQuestionStableId,
                                        String participantGuid,
                                        String instanceGuid,
                                        Integer value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new BoolAnswer(null, pepperQuestionStableId, null, getBooleanValue(value));
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public String answerAgreementQuestion(Handle handle,
                                          String pepperQuestionStableId,
                                          String participantGuid,
                                          String instanceGuid,
                                          Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new AgreementAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public String answerPickListQuestion(Handle handle, String questionStableId, String participantGuid, String instanceGuid,
                                         List<SelectedPicklistOption> selectedPicklistOptions, AnswerDao answerDao) {
        Answer answer = new PicklistAnswer(null, questionStableId, null, selectedPicklistOptions);
        return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
    }

    private SelectedPicklistOption getCountry(AboutYouSurvey aboutYouSurvey) throws Exception {
        Set<String> locales = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
        String country = aboutYouSurvey.getCountry();
        if (country == null) {
            return null;
        }
        if (!locales.contains(country)) {
            throw new Exception("Unexpected Country " + country);
        }

        return new SelectedPicklistOption(country);
    }

    private UserDto createUserForLegacyParticipant(
            Handle handle, Config cfg, DatstatParticipantData datstatParticipantData) throws Exception {

        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        String auth0ClientId = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_CLIENT_ID);
        LOG.info("Domain : {} ", auth0Domain);

        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
        StudyClientConfiguration studyClientConfiguration = handle.attach(ClientDao.class).getConfiguration(auth0ClientId, auth0Domain);
        if (studyClientConfiguration == null) {
            throw new Exception("Could not find study for domain " + auth0Domain);
        }
        LOG.info("Getting StudyClient Config for auth0clientId = " + auth0ClientId + " and domain = " + auth0Domain);

        Auth0Util auth0Util = new Auth0Util(auth0Domain);
        var mgmtClient = new Auth0ManagementClient(
                auth0Domain,
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());

        JdbiUser userDao = handle.attach(JdbiUser.class);
        JdbiClient clientDao = handle.attach(JdbiClient.class);

        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);

        UserDto pepperUser = createLegacyPepperUser(
                auth0Domain,
                studyClientConfiguration.getAuth0ClientId(),
                userDao,
                clientDao,
                auth0Util,
                mgmtClient,
                datstatParticipantData,
                userGuid,
                userHruid);

        return pepperUser;
    }

    /**
     * Creates a new legacy user.
     */
    public UserDto createLegacyPepperUser(String auth0Domain,
                                          String auth0ClientId,
                                          JdbiUser userDao,
                                          JdbiClient clientDao,
                                          Auth0Util auth0Util,
                                          Auth0ManagementClient mgmtClient,
                                          DatstatParticipantData data,
                                          String newUserGuid,
                                          String newUserHruid) throws Exception {
        String emailAddress = data.getDatstatEmail();

        // Create a user for the given domain
        String mgmtToken = mgmtClient.getToken();
        User newAuth0User = null;
        List<User> users = auth0Util.getAuth0UsersByEmail(emailAddress, mgmtToken, Auth0Util.USERNAME_PASSWORD_AUTH0_CONN_NAME);

        String randomPass = generateRandomPassword();
        if (users != null & users.size() > 0) {
            throw new UserExistsException("User " + emailAddress + " already present in Auth0");
        } else {
            newAuth0User = auth0Util.createAuth0User(emailAddress, randomPass, mgmtToken);
        }
        String auth0UserId = newAuth0User.getId();

        Optional<Long> pepperClientId = clientDao.getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
        if (!pepperClientId.isPresent()) {
            throw new DDPException("No client found for " + auth0ClientId);
        }

        String userCreatedAt = data.getDdpCreated();
        LocalDateTime createdAtDate = LocalDateTime.parse(userCreatedAt, DateTimeFormatter.ofPattern(DATSTAT_DATE_FORMAT));

        String lastModifiedStr = data.getDatstatLastmodified();
        LocalDateTime lastModifiedDate = createdAtDate;
        if (lastModifiedStr != null && !lastModifiedStr.isEmpty()) {
            lastModifiedDate = LocalDateTime.parse(lastModifiedStr);
        }

        long createdAtMillis = createdAtDate.toInstant(ZoneOffset.UTC).toEpochMilli();
        long updatedAtMillis = lastModifiedDate.toInstant(ZoneOffset.UTC).toEpochMilli();

        long userId = userDao.insertMigrationUser(auth0UserId, newUserGuid, pepperClientId.get(), newUserHruid,
                data.getDatstatAltpid(), data.getDdpParticipantShortid(), createdAtMillis, updatedAtMillis);
        UserDto newUser = new UserDto(userId, auth0UserId, newUserGuid, newUserHruid, data.getDatstatAltpid(),
                data.getDdpParticipantShortid(), createdAtMillis, updatedAtMillis);
        auth0Util.setDDPUserGuidForAuth0User(newUser.getUserGuid(), auth0UserId, auth0ClientId, mgmtToken);

        LOG.error("User created: Auth0UserId = " + auth0UserId + ", GUID = " + newUserGuid + ", HRUID = " + newUserHruid + ", ALTPID = "
                + data.getDatstatAltpid());
        return newUser;
    }

    private String generateRandomPassword() {
        Random rnd = new Random();
        int passLength = 128;

        StringBuilder stringBuilder = new StringBuilder(passLength);
        IntStream.range(0, passLength)
                .forEach(i -> stringBuilder.append(Character.toChars(rnd.nextInt(26) + 'a')));
        stringBuilder.replace(50, 54, "91_A<");

        return stringBuilder.toString();
    }

    UserProfile addUserProfile(UserDto user,
                               DatstatParticipantData data,
                               JdbiLanguageCode jdbiLanguageCode,
                               UserProfileDao profileDao) throws Exception {

        Boolean isDoNotContact = getBooleanValue(data.getDdpDoNotContact());
        Long languageCodeId = jdbiLanguageCode.getLanguageCodeId(DEFAULT_PREFERRED_LANGUAGE_CODE);

        UserProfile profile = new UserProfile.Builder(user.getUserId())
                .setFirstName(StringUtils.trim(data.getDatstatFirstname()))
                .setLastName(StringUtils.trim(data.getDatstatLastname()))
                .setPreferredLangId(languageCodeId)
                .setDoNotContact(isDoNotContact)
                .build();
        profileDao.createProfile(profile);

        return profile;
    }

    MailAddress addUserAddress(Handle handle, UserDto user,
                               DatstatParticipantData data,
                               ReleaseSurvey releaseSurvey,
                               JdbiMailAddress jdbiMailAddress,
                               OLCService olcService) throws Exception {

        int ddpValidationStatusCode = 0;
        if (data.getDdpAddressValid() != null) {
            ddpValidationStatusCode = Integer.parseInt(data.getDdpAddressValid());
        }

        DsmAddressValidationStatus status;
        if (ddpValidationStatusCode == 1) {
            status = DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS;
        } else {
            status = DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;
        }

        //query and set State value passed to state code
        String stateValue = data.getDdpState();
        if (StringUtils.isNotBlank(stateValue) && StringUtils.isNotBlank(data.getDdpCountry())) {
            String stateCode = getStateCode(handle, stateValue, data.getDdpCountry());
            if (StringUtils.isNotBlank(stateCode)) {
                stateValue = stateCode;
            }
        }

        String fullname = data.getDatstatFirstname().trim() + " " + data.getDatstatLastname().trim();
        MailAddress mailAddress = new MailAddress(fullname,
                data.getDdpStreet1(), data.getDdpStreet2(), data.getDdpCity(), stateValue, data.getDdpCountry(),
                data.getDdpPostalCode(), releaseSurvey != null ? releaseSurvey.getPhoneNumber() : null, null, null, status, true);
        mailAddress.setPlusCode(olcService.calculatePlusCodeWithPrecision(mailAddress, OLCService.DEFAULT_OLC_PRECISION));

        String ddpCreated = data.getDdpCreated();
        long ddpCreatedAt = Instant.now().toEpochMilli();
        //use DDP_CREATED time for MailAddress creation time.
        if (ddpCreated != null) {
            Instant instant = Instant.parse(ddpCreated);
            if (instant != null) {
                ddpCreatedAt = instant.toEpochMilli();
            }
        }

        MailAddress address = jdbiMailAddress.insertLegacyAddress(mailAddress, user.getUserGuid(), user.getUserGuid(), ddpCreatedAt);
        jdbiMailAddress.setDefaultAddressForParticipant(address.getGuid());
        LOG.info("Inserted address id: {}...createdTime: {} ", address.getGuid(), ddpCreatedAt);
        return mailAddress;
    }

    private String getStateCode(Handle handle, String ddpState, String ddpCountry) {
        String stateCode = null;
        JdbiCountrySubnationalDivision subnationDao = handle.attach(JdbiCountrySubnationalDivision.class);
        List<String> stateCodeList = subnationDao.getStateCode(ddpState.trim());
        if (stateCodeList.size() == 1) {
            stateCode = stateCodeList.get(0);
        } else if (stateCodeList.size() > 1) {
            //search and narrow down by country
            stateCode = subnationDao.getStateCode(ddpState.trim(), ddpCountry.trim());
        }

        return stateCode;
    }


    Long addKitDetails(DsmKitRequestDao dsmKitRequestDao,
                       KitTypeDao kitTypeDao,
                       Long pepperUserId,
                       Long addressid,
                       DatstatParticipantData data,
                       String studyGuid) {

        ZoneId zoneId = ZoneId.of("UTC");
        long epoch = DEFAULT_MIGRATION_KIT_CREATE_DATE.atStartOfDay(zoneId).toEpochSecond();
        long kitTypeId = kitTypeDao.getSalivaKitType().getId();
        long kitId = dsmKitRequestDao.createKitRequest(data.getDdpSpitKitRequestId(), studyGuid,
                addressid, kitTypeId, pepperUserId, epoch, false);
        LOG.info("Created kit ID: " + kitId);
        return kitId;
    }

    void addUserStudyExit(Handle handle, ParticipantData participantData,
                          String participantGuid,
                          String studyGuid) {
        DatstatParticipantData data = participantData.getParticipantUser().getDatstatparticipantdata();
        LocalDateTime exitAt = null;
        if (data.getDdpExited() != null && !data.getDdpExited().isEmpty()) {
            exitAt = LocalDateTime.parse(data.getDdpExited(), DateTimeFormatter.ofPattern(DATSTAT_DATE_FORMAT));

            handle.attach(JdbiUserStudyEnrollment.class).terminateStudyEnrollment(participantGuid, studyGuid,
                    exitAt.toEpochSecond(ZoneOffset.UTC) * MILLIS_PER_SECOND);
        }
    }

    private Boolean getBooleanValue(Integer value) throws Exception {
        if (value == null) {
            return null;
        } else if (value == 0) {
            return false;
        } else if (value == 1) {
            return true;
        } else {
            throw new Exception("Unexpected value while parsing gen2 boolean: " + value);
        }
    }

    class UserExistsException extends Exception {
        UserExistsException(String msg) {
            super(msg);
        }
    }

}
