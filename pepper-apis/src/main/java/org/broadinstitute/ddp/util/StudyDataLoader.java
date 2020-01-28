package org.broadinstitute.ddp.util;

import static org.apache.commons.lang.time.DateUtils.MILLIS_PER_SECOND;

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
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.auth0.json.mgmt.users.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants.MedicalProviderTable;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCountrySubnationalDivision;
import org.broadinstitute.ddp.db.dao.JdbiInstitutionType;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.exception.AddressVerificationException;
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
import org.broadinstitute.ddp.model.migration.BaseSurvey;
import org.broadinstitute.ddp.model.migration.SurveyAddress;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.OLCService;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyDataLoader {
    public static final String OTHER = "OTHER";
    //public static final String NED = "NED";
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String DK = "DK";
    private static final Logger LOG = LoggerFactory.getLogger(StudyDataLoader.class);
    private static final String DEFAULT_PREFERRED_LANGUAGE_CODE = "en";
    private static final String DATSTAT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final int DSM_DEFAULT_ON_DEMAND_TRIGGER_ID = -2;
    private Long defaultKitCreationEpoch = null;

    Map<String, List<String>> sourceDataSurveyQs;
    Map<String, String> altNames;
    Map<Integer, String> yesNoDkLookup;
    Map<Integer, Boolean> booleanValueLookup;
    Auth0Util auth0Util;
    String auth0Domain;
    String auth0ClientId;
    String mgmtToken;
    Long pepperClientId;

    public StudyDataLoader(Config cfg) {

        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);

        auth0Util = new Auth0Util(auth0Domain);
        Auth0MgmtTokenHelper tokenHelper = new Auth0MgmtTokenHelper(auth0Config.getString("managementApiClientId"),
                auth0Config.getString("managementApiSecret"),
                auth0Domain);
        mgmtToken = tokenHelper.getManagementApiToken();

        sourceDataSurveyQs = new HashMap<>();

        //some lookup codes/values
        yesNoDkLookup = new HashMap<>();
        yesNoDkLookup.put(0, "NO");
        yesNoDkLookup.put(1, "YES");
        yesNoDkLookup.put(2, "DK");

        booleanValueLookup = new HashMap<>();
        booleanValueLookup.put(0, false);
        booleanValueLookup.put(1, true);

        altNames = new HashMap<>();
        altNames.put("dk", "Don't know");
        altNames.put("AMERICAN_INDIAN", "American Indian or Native American");
        altNames.put("OTHER_EAST_ASIAN", "Other East Asian");
        altNames.put("SOUTH_EAST_ASIAN", "South East Asian or Indian");
        altNames.put("BLACK", "Black or African American");
        altNames.put("NATIVE_HAWAIIAN", "Native Hawaiian or other Pacific Islander");
        altNames.put("PREFER_NOT_ANSWER", "I prefer not to answer");

        altNames.put("AXILLARY_LYMPH_NODES", "aux_lymph_node");
        altNames.put("OTHER_LYMPH_NODES", "other_lymph_node");

        altNames.put("drugstart_year", "drugstartyear");
        altNames.put("drugstart_month", "drugstartmonth");
        altNames.put("drugend_year", "drugendyear");
        altNames.put("drugend_month", "drugendmonth");
    }

    void loadMailingListData(Handle handle, JsonElement data, String studyCode) {
        LOG.info("loading: {} mailinglist", studyCode);
        JdbiMailingList dao = handle.attach(JdbiMailingList.class);
        JsonArray dataArray = data.getAsJsonArray();
        Long dateCreatedMillis = null;
        JsonElement dateCreatedEl;
        String firstName;
        String lastName;
        String email;
        for (JsonElement thisEl : dataArray) {
            dateCreatedEl = thisEl.getAsJsonObject().get("datecreated");
            if (dateCreatedEl != null && !dateCreatedEl.isJsonNull()) {
                dateCreatedMillis = dateCreatedEl.getAsNumber().longValue() * MILLIS_PER_SECOND;
            }
            firstName = getStringValueFromElement(thisEl, "firstname");
            lastName = getStringValueFromElement(thisEl, "lastname");
            email = getStringValueFromElement(thisEl, "email");
            dao.insertByStudyGuidIfNotStoredAlready(firstName, lastName, email,
                    studyCode, null, dateCreatedMillis);
        }
    }

    public Map<String, String> verifyAuth0Users(Set<String> emailList) {
        //make auth0 call
        Map<String, String> auth0EmailMap = auth0Util.getAuth0UsersByEmails(emailList, mgmtToken);
        return auth0EmailMap;
    }

    public String loadParticipantData(Handle handle, JsonElement datstatData, JsonElement mappingData, String phoneNumber,
                                      StudyDto studyDto, MailAddress address, OLCService olcService,
                                      AddressService addressService) throws Exception {

        //load data
        JdbiUser jdbiUser = handle.attach(JdbiUser.class);
        String altpid = datstatData.getAsJsonObject().get("datstat_altpid").getAsString();
        String userGuid = jdbiUser.getUserGuidByAltpid(altpid);
        if (userGuid != null) {
            LOG.warn("Looks like  Participant data already loaded: " + userGuid);
            return userGuid;
            //watch out.. early return
        }

        //get default kit creation date
        String defaultKitCreationDate = mappingData.getAsJsonArray().get(2).getAsJsonObject()
                .get("default_kit_creation_date").getAsString();
        userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);
        JdbiUser userDao = handle.attach(JdbiUser.class);
        JdbiClient clientDao = handle.attach(JdbiClient.class);

        UserDto pepperUser = createLegacyPepperUser(userDao, clientDao, datstatData, userGuid, userHruid);

        JdbiProfile jdbiProfile = handle.attach(JdbiProfile.class);
        JdbiLanguageCode jdbiLanguageCode = handle.attach(JdbiLanguageCode.class);
        addUserProfile(pepperUser, datstatData, jdbiLanguageCode, jdbiProfile);

        JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
        MailAddress createdAddress = addUserAddress(handle, pepperUser,
                datstatData,
                phoneNumber, address,
                jdbiMailAddress,
                olcService, addressService);

        String kitRequestId = getStringValueFromElement(datstatData, "ddp_spit_kit_request_id");
        if (kitRequestId != null) {
            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            DsmKitRequestDao dsmKitRequestDao = handle.attach(DsmKitRequestDao.class);
            addKitDetails(dsmKitRequestDao,
                    kitTypeDao,
                    pepperUser.getUserId(),
                    createdAddress.getId(),
                    kitRequestId,
                    studyDto.getGuid(),
                    defaultKitCreationDate);
        }

        String ddpCreated = getStringValueFromElement(datstatData, "ddp_created");
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
                .changeUserStudyEnrollmentStatus(userGuid, studyDto.getGuid(), EnrollmentStatusType.REGISTERED, ddpCreatedAt);

        LOG.info("user guid: " + pepperUser.getUserGuid());
        processLegacyFields(handle, datstatData, mappingData.getAsJsonArray().get(1),
                studyDto.getId(), pepperUser.getUserId(), null);

        return pepperUser.getUserGuid();
    }


    public ActivityInstanceDto createActivityInstance(JsonElement surveyData, String participantGuid,
                                                      long studyId, String activityCode, String createdAt,
                                                      JdbiActivity jdbiActivity,
                                                      ActivityInstanceDao activityInstanceDao,
                                                      ActivityInstanceStatusDao activityInstanceStatusDao) throws Exception {

        BaseSurvey baseSurvey = getBaseSurvey(surveyData);
        if (baseSurvey.getDdpCreated() == null) {
            LOG.warn("No createdAt for survey: {} participant guid: {} . using participant data created_at ",
                    activityCode, participantGuid);
            baseSurvey.setDdpCreated(createdAt);
        }
        Long submissionId = baseSurvey.getDatstatSubmissionId();
        String sessionId = baseSurvey.getDatstatSessionId();
        String ddpCreated = baseSurvey.getDdpCreated();
        String ddpCompleted = baseSurvey.getDdpFirstCompleted();
        String ddpLastUpdated = baseSurvey.getDdpLastUpdated();
        String activityVersion = baseSurvey.getActivityVersion();
        if (StringUtils.isEmpty(activityVersion)) {
            activityVersion = baseSurvey.getSurveyVersion();
        }
        Integer submissionStatus = baseSurvey.getDatstatSubmissionStatus();
        Long studyActivityId = jdbiActivity.findIdByStudyIdAndCode(studyId, activityCode).get();
        Long ddpLastUpdatedAt;
        Long ddpCreatedAt;
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
            ddpLastUpdatedAt = instant.toEpochMilli();
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
            if (ddpCompletedAt < ddpCreatedAt) {
                throw new Exception("Invalid ddpCreatedAt - ddpCompletedAt dates. created date : " + ddpCreated
                        + " is greater than ddpCompleted/Submitted date:: " + ddpCompleted + " in activity: " + activityCode
                        + " userguid: " + participantGuid);
            }
        }

        if (ddpLastUpdatedAt < ddpCreatedAt) {
            throw new Exception("Invalid ddpCreatedAt - ddpLastUpdated dates. created date : " + ddpCreated
                    + " is greater than last updated date: " + ddpLastUpdated + " in activity: " + activityCode
                    + " userguid: " + participantGuid);
        }

        String surveyStatus = baseSurvey.getSurveyStatus();
        InstanceStatusType instanceCurrentStatus = null;
        if (StringUtils.isNotEmpty(surveyStatus)) {
            instanceCurrentStatus = InstanceStatusType.valueOf(surveyStatus);
            if ("CREATED".equalsIgnoreCase(instanceCurrentStatus.name()) && ddpCreatedAt.compareTo(ddpLastUpdatedAt) != 0) {
                throw new Exception("Passed survey status as CREATED but lastUpdated date: " + ddpLastUpdated
                        + " is not same as created date: " + createdAt);
            }
        } else {
            instanceCurrentStatus = getActivityInstanceStatus(submissionStatus, ddpCreatedAt, ddpLastUpdatedAt, ddpCompletedAt);
        }

        // Read only is always false for things that aren't consent- we rely on the user being terminated to show read only activities
        boolean isReadonly = false;
        if ((activityCode == "CONSENT" || activityCode == "TISSUECONSENT" || activityCode == "BLOODCONSENT")
                && instanceCurrentStatus == InstanceStatusType.COMPLETE) {
            isReadonly = true;
        }
        ActivityInstanceDto dto = activityInstanceDao
                .insertInstance(studyActivityId, participantGuid, participantGuid, InstanceStatusType.CREATED,
                        isReadonly,
                        ddpCreatedAt,
                        submissionId, sessionId, activityVersion);

        LOG.info("Created activity instance {} for activity {} and user {}",
                dto.getGuid(), activityCode, participantGuid);

        long activityInstanceId = dto.getId();

        if (InstanceStatusType.IN_PROGRESS == instanceCurrentStatus) {
            activityInstanceStatusDao
                    .insertStatus(activityInstanceId, InstanceStatusType.IN_PROGRESS, ddpLastUpdatedAt, participantGuid);
        } else if (InstanceStatusType.COMPLETE == instanceCurrentStatus) {
            if (ddpCompletedAt == null) {
                //ddpCompletedAt = ddpLastUpdatedAt;
                throw new Exception("No completed/submitted date value passed for " + activityCode
                        + " survey with status COMPLETE. user guid: " + participantGuid);
            }
            activityInstanceStatusDao.insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, ddpCompletedAt, participantGuid);
            if (ddpLastUpdatedAt > ddpCompletedAt) {
                activityInstanceStatusDao
                        .insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, ddpLastUpdatedAt, participantGuid);
            }
        } else {
            //CREATED
            activityInstanceStatusDao.insertStatus(activityInstanceId, InstanceStatusType.CREATED, ddpCreatedAt, participantGuid);
        }

        return dto;
    }

    public void loadAboutYouSurveyData(Handle handle,
                                       JsonElement surveyData,
                                       JsonElement mappingData,
                                       StudyDto studyDto,
                                       UserDto userDto,
                                       ActivityInstanceDto instanceDto,
                                       AnswerDao answerDao) throws Exception {

        LOG.info("Populating AboutYou Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO AboutYou Survey !");
            return;
        }

        processSurveyData(handle, "aboutyousurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);
    }

    private InstanceStatusType getActivityInstanceStatus(Integer submissionStatusCode, Long createdAt,
                                                         Long lastUpdatedAt, Long completedAt) throws Exception {
        //submissionStatusCode: Completed = 1 ; In Progress = 2; Terminated = 5
        //mbc gets instance status passed.really don't need to determine status.. May be for future studies

        InstanceStatusType statusType;
        if (completedAt != null) {
            if (submissionStatusCode != null && submissionStatusCode != 1 && submissionStatusCode != 5) {
                throw new Exception("The survey has a completedAt value, but claims to not be completed!");
            }
            statusType = InstanceStatusType.COMPLETE;
        } else if (createdAt != null && !createdAt.equals(lastUpdatedAt)) {
            if (submissionStatusCode != null && submissionStatusCode != 2 && submissionStatusCode != 5) {
                throw new Exception("The survey has a different createdAt/lastUpdatedAt values, but claims to not be in progress!");
            }
            statusType = InstanceStatusType.IN_PROGRESS;
        } else {
            if (submissionStatusCode != null && submissionStatusCode != 2 && submissionStatusCode != 5) {
                throw new Exception("The survey has the same createdAt/lastUpdatedAt values, but claims to not be in progress!");
            }
            statusType = InstanceStatusType.CREATED;
        }

        //if no status code passed try by timestamps/dates passed
        if (statusType == null) {
            //figure out different way
            if (completedAt != null) {
                statusType = InstanceStatusType.COMPLETE;
            } else if (lastUpdatedAt != null) {
                statusType = InstanceStatusType.IN_PROGRESS;
            } else {
                //if we got this far.. its created
                statusType = InstanceStatusType.CREATED;
            }
        }

        return statusType;
    }

    private void addLegacySurveyAddress(Handle handle, StudyDto studyDto, UserDto userDto, ActivityInstanceDto instanceDto,
                                        JsonElement data, String surveyName) {

        String street1 = getStringValueFromElement(data, "street1");
        String street2 = getStringValueFromElement(data, "street2");
        String city = getStringValueFromElement(data, "city");
        String country = getStringValueFromElement(data, "country");
        String postalCode = getStringValueFromElement(data, "postal_code");
        String state = getStringValueFromElement(data, "state");
        String fullName = getStringValueFromElement(data, "fullname");
        String phoneNumber = getStringValueFromElement(data, "phone_number");

        SurveyAddress userLegacyAddress = new SurveyAddress(fullName, street1, street2,
                city, state, country, postalCode, phoneNumber);

        String fieldName = "legacy_".concat(surveyName).concat("_address");

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.serializeNulls().create();
        String addressJsonStr = gson.toJson(userLegacyAddress);
        handle.attach(JdbiUserStudyLegacyData.class).insert(userDto.getUserId(), studyDto.getId(), instanceDto.getId(),
                fieldName, addressJsonStr);
    }

    public void loadReleaseSurveyData(Handle handle,
                                      JsonElement surveyData,
                                      JsonElement mappingData,
                                      StudyDto studyDto,
                                      UserDto userDto,
                                      ActivityInstanceDto instanceDto,
                                      AnswerDao answerDao) throws Exception {

        LOG.info("Populating Release Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Release Survey !");
            return;
        }

        addLegacySurveyAddress(handle, studyDto, userDto, instanceDto, surveyData, "tissuerelease");

        processSurveyData(handle, "releasesurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);

        //handle agreement
        String surveyStatus = surveyData.getAsJsonObject().get("survey_status").getAsString();
        if (surveyStatus.equalsIgnoreCase("COMPLETE")) {
            answerAgreementQuestion(handle, "TISSUERELEASE_AGREEMENT", userDto.getUserGuid(),
                    instanceDto.getGuid(), Boolean.TRUE, answerDao);
        }

        processInstitutions(handle, surveyData, userDto, studyDto,
                "physician_list", InstitutionType.PHYSICIAN, "releasesurvey", instanceDto);
        processInstitutions(handle, surveyData, userDto, studyDto,
                "institution_list", InstitutionType.INITIAL_BIOPSY, "releasesurvey", instanceDto);

        updateUserStudyEnrollment(handle, surveyData, userDto.getUserGuid(), studyDto.getGuid());

    }


    public void loadBloodReleaseSurveyData(Handle handle,
                                           JsonElement surveyData,
                                           JsonElement mappingData,
                                           StudyDto studyDto,
                                           UserDto userDto,
                                           ActivityInstanceDto instanceDto,
                                           AnswerDao answerDao) throws Exception {

        LOG.info("Populating Blood Release Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Release Survey !");
            return;
        }

        processSurveyData(handle, "bdreleasesurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);

        //handle agreement
        String surveyStatus = surveyData.getAsJsonObject().get("survey_status").getAsString();
        if (surveyStatus.equalsIgnoreCase("COMPLETE")) {
            answerAgreementQuestion(handle, "BLOODRELEASE_AGREEMENT", userDto.getUserGuid(),
                    instanceDto.getGuid(), Boolean.TRUE, answerDao);
        }

        //Special case for MBC.
        //MBC might have dup physicians because physician list comes in both release and bdrelease surveys
        processPhysicianList(handle, surveyData, userDto, studyDto,
                "physician_list", InstitutionType.PHYSICIAN, "bdreleasesurvey", instanceDto);
    }


    private void updateUserStudyEnrollment(Handle handle, JsonElement surveyData, String userGuid, String studyGuid) throws Exception {
        BaseSurvey baseSurvey = getBaseSurvey(surveyData);
        if (InstanceStatusType.COMPLETE.name().equalsIgnoreCase(baseSurvey.getSurveyStatus())) {
            long updatedAt;
            if (baseSurvey.getDdpFirstCompleted() != null) {
                Instant instant;
                try {
                    instant = Instant.parse(baseSurvey.getDdpFirstCompleted());
                } catch (DateTimeParseException e) {
                    throw new Exception("Could not parse required completedAt value:" + baseSurvey.getDdpFirstCompleted());
                }
                updatedAt = instant.toEpochMilli();
            } else {
                Instant instant;
                try {
                    instant = Instant.parse(baseSurvey.getDdpLastUpdated());
                } catch (DateTimeParseException e) {
                    throw new Exception("Could not parse required lastUpdated value:" + baseSurvey.getDdpLastUpdated());
                }
                updatedAt = instant.toEpochMilli();
            }

            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(userGuid, studyGuid,
                    EnrollmentStatusType.ENROLLED, updatedAt);
        }
    }


    String getMedicalProviderGuid(Handle handle) {
        return DBUtils.uniqueStandardGuid(handle,
                MedicalProviderTable.TABLE_NAME, MedicalProviderTable.MEDICAL_PROVIDER_GUID);
    }

    public void loadConsentSurveyData(Handle handle,
                                      JsonElement surveyData,
                                      JsonElement mappingData,
                                      StudyDto studyDto,
                                      UserDto userDto,
                                      ActivityInstanceDto instanceDto,
                                      AnswerDao answerDao) throws Exception {

        LOG.info("Populating Consent Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Consent Survey !");
            return;
        }

        processSurveyData(handle, "consentsurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);
    }

    public void loadTissueConsentSurveyData(Handle handle,
                                            JsonElement surveyData,
                                            JsonElement mappingData,
                                            StudyDto studyDto,
                                            UserDto userDto,
                                            ActivityInstanceDto instanceDto,
                                            AnswerDao answerDao) throws Exception {

        LOG.info("Populating Tissue Consent Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Tissue Consent Survey !");
            return;
        }

        //for mbc tissueconsentsurvey data comes as consent
        processSurveyData(handle, "consentsurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);
    }

    public void loadBloodConsentSurveyData(Handle handle,
                                           JsonElement surveyData,
                                           JsonElement mappingData,
                                           StudyDto studyDto,
                                           UserDto userDto,
                                           ActivityInstanceDto instanceDto,
                                           AnswerDao answerDao) throws Exception {

        LOG.info("Populating Blood Consent Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Blood Consent Survey !");
            return;
        }

        processSurveyData(handle, "bdconsentsurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);

        String street1 = getStringValueFromElement(surveyData, "street1");
        String street2 = getStringValueFromElement(surveyData, "street2");
        String city = getStringValueFromElement(surveyData, "city");
        String postalCode = getStringValueFromElement(surveyData, "postal_code");
        String state = getStringValueFromElement(surveyData, "state");
        String phoneNumber = getStringValueFromElement(surveyData, "phone_number");

        String bdConsentAddress =
                Stream.of(street1, street2, city, state, postalCode)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(", "));

        if (StringUtils.isNotBlank(bdConsentAddress)) {
            answerTextQuestion(handle, "BLOODCONSENT_ADDRESS", userDto.getUserGuid(), instanceDto.getGuid(),
                    bdConsentAddress, answerDao);
        }
        if (StringUtils.isNotBlank(phoneNumber)) {
            answerTextQuestion(handle, "BLOODCONSENT_PHONE", userDto.getUserGuid(), instanceDto.getGuid(),
                    phoneNumber, answerDao);
        }
        //addLegacySurveyAddress(handle, studyDto, userDto, instanceDto, surveyData, "bloodconsent");
    }

    public void loadFollowupSurveyData(Handle handle,
                                       JsonElement surveyData,
                                       JsonElement mappingData,
                                       StudyDto studyDto,
                                       UserDto userDto,
                                       ActivityInstanceDto instanceDto,
                                       JdbiActivityInstance activityInstanceDao,
                                       AnswerDao answerDao) throws Exception {

        LOG.info("Populating Followup Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO Followup Survey !");
            return;
        }

        processSurveyData(handle, "followupsurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);

        Integer dsmTriggerId = getIntegerValueFromElement(surveyData, "ddp_dsmtriggerid");
        activityInstanceDao.updateOndemandTriggerId(userDto.getUserId(), instanceDto.getId(),
                dsmTriggerId == null ? DSM_DEFAULT_ON_DEMAND_TRIGGER_ID : dsmTriggerId.intValue());
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

    public String answerTextQuestion(Handle handle,
                                     String pepperQuestionStableId,
                                     String participantGuid,
                                     String instanceGuid,
                                     String value, AnswerDao answerDao) {
        String guid = null;
        if (value != null) {
            Answer answer = new TextAnswer(null, pepperQuestionStableId, null, value);
            guid = answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return guid;
    }

    public String answerBooleanQuestion(Handle handle,
                                        String pepperQuestionStableId,
                                        String participantGuid,
                                        String instanceGuid,
                                        Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new BoolAnswer(null, pepperQuestionStableId, null, value);
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

    public String answerCompositeQuestion(Handle handle,
                                          String pepperQuestionStableId,
                                          String participantGuid,
                                          String instanceGuid,
                                          List<String> nestedGuids, List<Integer> compositeAnswerOrders, AnswerDao answerDao) {

        Answer parentAnswer = answerDao.createAnswer(participantGuid, instanceGuid,
                new CompositeAnswer(null, pepperQuestionStableId, null));
        List<Long> childrenAnswerIds = new ArrayList<>();

        var jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);
        if (CollectionUtils.isNotEmpty(nestedGuids)) {
            for (String childGuid : nestedGuids) {
                childrenAnswerIds.add(answerDao.getAnswerSql().findDtoByGuid(childGuid).get().getId());
            }
            jdbiCompositeAnswer.insertChildAnswerItems(parentAnswer.getAnswerId(), childrenAnswerIds, compositeAnswerOrders);
        }
        return parentAnswer.getAnswerGuid();
    }

    public UserDto createLegacyPepperUser(JdbiUser userDao, JdbiClient clientDao,
                                          JsonElement data, String userGuid, String userHruid) throws Exception {

        String emailAddress = data.getAsJsonObject().get("datstat_email").getAsString();

        // Create a user for the given domain
        String randomPass = generateRandomPassword();
        User newAuth0User = auth0Util.createAuth0User(emailAddress, randomPass, mgmtToken);

        String auth0UserId = newAuth0User.getId();

        if (pepperClientId == null) {
            Optional<Long> pepperClientIdEl = clientDao.getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
            if (!pepperClientIdEl.isPresent()) {
                throw new DDPException("No client found for " + auth0ClientId);
            }
            pepperClientId = pepperClientIdEl.get();
        }

        String userCreatedAt = getStringValueFromElement(data, "ddp_created");
        LocalDateTime createdAtDate = LocalDateTime.parse(userCreatedAt, DateTimeFormatter.ofPattern(DATSTAT_DATE_FORMAT));

        String lastModifiedStr = getStringValueFromElement(data, "ddp_last_updated");
        LocalDateTime lastModifiedDate = createdAtDate;
        if (lastModifiedStr != null && !lastModifiedStr.isEmpty()) {
            lastModifiedDate = LocalDateTime.parse(lastModifiedStr);
        }

        long createdAtMillis = createdAtDate.toInstant(ZoneOffset.UTC).toEpochMilli();
        long updatedAtMillis = lastModifiedDate.toInstant(ZoneOffset.UTC).toEpochMilli();

        String shortId = data.getAsJsonObject().get("ddp_participant_shortid").getAsString();
        String altpid = data.getAsJsonObject().get("datstat_altpid").getAsString();
        long userId = userDao.insertMigrationUser(auth0UserId, userGuid, pepperClientId, userHruid,
                altpid, shortId, createdAtMillis, updatedAtMillis);
        UserDto newUser = new UserDto(userId, auth0UserId, userGuid, userHruid, altpid,
                shortId, createdAtMillis, updatedAtMillis);
        auth0Util.setDDPUserGuidForAuth0User(newUser.getUserGuid(), auth0UserId, auth0ClientId, mgmtToken);

        LOG.info("User created: Auth0UserId = " + auth0UserId + ", GUID = " + userGuid + ", HRUID = " + userHruid + ", ALTPID = "
                + altpid);
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

    UserProfileDto addUserProfile(UserDto user,
                                  JsonElement data,
                                  JdbiLanguageCode jdbiLanguageCode,
                                  JdbiProfile jdbiProfile) throws Exception {

        Boolean isDoNotContact = getBooleanValueFromElement(data, "ddp_do_not_contact");
        Long languageCodeId = jdbiLanguageCode.getLanguageCodeId(DEFAULT_PREFERRED_LANGUAGE_CODE);

        UserProfileDto userProfileDto = new UserProfileDto(user.getUserId(),
                data.getAsJsonObject().get("datstat_firstname").getAsString(),
                data.getAsJsonObject().get("datstat_lastname").getAsString(),
                null, null, languageCodeId,
                DEFAULT_PREFERRED_LANGUAGE_CODE, isDoNotContact);

        int numRowsInserted = jdbiProfile.insert(userProfileDto);

        if (numRowsInserted != 1) {
            LOG.info("Failed to save profile information.  " + numRowsInserted + " rows were updated");
            throw new DaoException("Failed to save profile information.  " + numRowsInserted + " rows were updated");
        }

        return userProfileDto;
    }

    MailAddress getUserAddress(Handle handle, JsonElement data,
                               String phoneNumber,
                               OLCService olcService, AddressService addressService) {

        String street1 = getStringValueFromElement(data, "ddp_street1");
        String street2 = getStringValueFromElement(data, "ddp_street2");
        String city = getStringValueFromElement(data, "ddp_city");
        String country = getStringValueFromElement(data, "ddp_country");
        String postalCode = getStringValueFromElement(data, "ddp_postal_code");
        String state = getStringValueFromElement(data, "ddp_state");
        String firstName = getStringValueFromElement(data, "datstat_firstname");
        String lastName = getStringValueFromElement(data, "datstat_lastname");
        String fullName = firstName.trim().concat(" ").concat(lastName.trim());

        if (StringUtils.isNotBlank(state) && StringUtils.isNotBlank(country)) {
            String stateCode = getStateCode(handle, state, country);
            if (StringUtils.isNotBlank(stateCode)) {
                state = stateCode;
            }
        }

        MailAddress mailAddress = new MailAddress(fullName,
                street1, street2, city, state, country,
                postalCode, phoneNumber, null, null, null, true);

        //no addressvalid flag in MBC.
        //if kit exists consider address as valid else validate address
        String kitRequestId = getStringValueFromElement(data, "ddp_spit_kit_request_id");
        if (StringUtils.isNotBlank(kitRequestId)) {
            mailAddress.setValidationStatus(DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS);
            mailAddress.setPlusCode(olcService.calculatePlusCodeWithPrecision(mailAddress, OLCService.DEFAULT_OLC_PRECISION));
        } else {
            try {
                mailAddress = addressService.verifyAddress(mailAddress);
                mailAddress.setValidationStatus(DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS);
            } catch (AddressVerificationException e) {
                //LOG.warn("Exception while verifying address for user: {} error: {} ", user.getUserGuid(), e);
                mailAddress.setValidationStatus(DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
            }
        }

        return mailAddress;
    }


    MailAddress addUserAddress(Handle handle, UserDto user,
                               JsonElement data,
                               String phoneNumber, MailAddress mailAddress,
                               JdbiMailAddress jdbiMailAddress,
                               OLCService olcService, AddressService addressService) {

        if (mailAddress == null) {
            mailAddress = getUserAddress(handle, data, phoneNumber, olcService, addressService);
        }

        String ddpCreated = getStringValueFromElement(data, "ddp_created");
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
        LOG.info("Inserted address id: {}...createdTime: {}", address.getGuid(), ddpCreatedAt);
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


    private BaseSurvey getBaseSurvey(JsonElement surveyData) {

        Integer datstatSubmissionIdNum = getIntegerValueFromElement(surveyData, "datstat.submissionid");
        Long datstatSubmissionId = null;
        if (datstatSubmissionIdNum != null) {
            datstatSubmissionId = datstatSubmissionIdNum.longValue();
        }
        String datstatSessionId = getStringValueFromElement(surveyData, "datstat.sessionid");
        String ddpCreated = getStringValueFromElement(surveyData, "ddp_created");
        String ddpFirstCompleted = getStringValueFromElement(surveyData, "ddp_firstcompleted");
        String ddpLastSubmitted = getStringValueFromElement(surveyData, "ddp_lastsubmitted");
        String ddpLastUpdated = getStringValueFromElement(surveyData, "ddp_lastupdated");
        String surveyVersion = getStringValueFromElement(surveyData, "surveyversion");
        String activityVersion = getStringValueFromElement(surveyData, "consent_version");
        String surveyStatus = getStringValueFromElement(surveyData, "survey_status");
        Integer datstatSubmissionStatus = getIntegerValueFromElement(surveyData, "datstat.submissionstatus");

        if (ddpFirstCompleted == null) {
            ddpFirstCompleted = ddpLastSubmitted;
        }

        BaseSurvey baseSurvey = new BaseSurvey(datstatSubmissionId, datstatSessionId, ddpCreated, ddpFirstCompleted,
                ddpLastUpdated, surveyVersion, activityVersion, surveyStatus, datstatSubmissionStatus);
        return baseSurvey;
    }

    private String getStringValueFromElement(JsonElement element, String key) {
        String value = null;
        JsonElement keyEl = element.getAsJsonObject().get(key);
        if (keyEl != null && !keyEl.isJsonNull()) {
            value = keyEl.getAsString();
        }
        return value;
    }

    private Boolean getBooleanValueFromElement(JsonElement element, String key) {
        Boolean value = null;
        JsonElement keyEl = element.getAsJsonObject().get(key);
        if (keyEl != null && !keyEl.isJsonNull()) {
            value = keyEl.getAsBoolean();
        }
        return value;
    }

    private Integer getIntegerValueFromElement(JsonElement element, String key) {
        Integer value = null;
        JsonElement keyEl = element.getAsJsonObject().get(key);
        if (keyEl != null && !keyEl.isJsonNull()) {
            value = keyEl.getAsInt();
        }
        return value;
    }

    Long addKitDetails(DsmKitRequestDao dsmKitRequestDao,
                       KitTypeDao kitTypeDao,
                       Long pepperUserId,
                       Long addressid,
                       String kitRequestId,
                       String studyGuid,
                       String defaultKitCreationDate) {

        if (defaultKitCreationEpoch == null) {
            ZoneId zoneId = ZoneId.of("UTC");
            defaultKitCreationEpoch = LocalDate.parse(defaultKitCreationDate,
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")).atStartOfDay(zoneId).toEpochSecond();
        }
        long kitTypeId = kitTypeDao.getSalivaKitType().getId();
        long kitId = dsmKitRequestDao.createKitRequest(kitRequestId, studyGuid, addressid, kitTypeId,
                pepperUserId, defaultKitCreationEpoch);
        LOG.info("Created kit ID: " + kitId);
        return kitId;
    }

    void addUserStudyExit(Handle handle, String ddpExited,
                          String participantGuid,
                          String studyGuid) {
        LocalDateTime exitAt;
        if (ddpExited != null && !ddpExited.isEmpty()) {
            exitAt = LocalDateTime.parse(ddpExited, DateTimeFormatter.ofPattern(DATSTAT_DATE_FORMAT));

            handle.attach(JdbiUserStudyEnrollment.class).terminateStudyEnrollment(participantGuid, studyGuid,
                    exitAt.toEpochSecond(ZoneOffset.UTC) * MILLIS_PER_SECOND);
        }
    }

    private void processSurveyData(Handle handle, String surveyName, JsonElement sourceData, JsonElement mappingData, StudyDto studyDto,
                                   UserDto userDto, ActivityInstanceDto instanceDto, AnswerDao answerDao) throws Exception {

        //if survey is null.. just return
        if (sourceData == null || sourceData.isJsonNull()) {
            LOG.warn("no source data for survey: {}", surveyName);
            return;
        }

        String participantGuid = userDto.getUserGuid();
        String instanceGuid = instanceDto.getGuid();

        sourceDataSurveyQs.put(surveyName, new ArrayList<String>());
        //iterate through mappingData and try to retrieve sourceData for each element
        //iterate through each question_stable_mapping
        JsonArray questionStableArray = mappingData.getAsJsonObject().getAsJsonArray("question_answer_stables");
        for (JsonElement thisMap : questionStableArray) {
            String questionName = getStringValueFromElement(thisMap, "name");
            String questionType = getStringValueFromElement(thisMap, "type");
            String stableId = getStringValueFromElement(thisMap, "stable_id");
            if (StringUtils.isEmpty(stableId)) {
                //non question-answer-stable elements.. continue to next element
                sourceDataSurveyQs.get(surveyName).add(questionName);
                continue;
            }

            //Now try to get source data for this question
            //check type and act accordingly
            switch (questionType) {
                case "Date":
                    processDateQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "string":
                    processTextQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Picklist":
                    processPicklistQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                //case "YesNoDkPicklist":
                //    processYesNoDkPicklistQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                //    break; //todo
                case "Boolean":
                    processBooleanQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "BooleanSpecialPL":
                    processBooleanSpecialPLQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Agreement":
                    processAgreementQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Composite":
                    processCompositeQuestion(handle, thisMap, sourceData, surveyName,
                            participantGuid, instanceGuid, answerDao);
                    break;
                case "CompositeMedList":
                    processMedListCompositeQuestion(handle, thisMap, sourceData, surveyName,
                            participantGuid, instanceGuid, answerDao);
                    break;
                default:
                    LOG.warn(" Default .. Q name: {} .. type: {} ", questionName, questionType);
            }
        }

        processLegacyFields(handle, sourceData, mappingData, studyDto.getId(), userDto.getUserId(), instanceDto.getId());

    }

    private void processLegacyFields(Handle handle, JsonElement sourceData, JsonElement mappingData,
                                     long studyId, long participantId, Long instanceId) {

        JsonElement legacyFieldsJsonEl = mappingData.getAsJsonObject().get("legacy_fields");
        if (legacyFieldsJsonEl == null || legacyFieldsJsonEl.isJsonNull()) {
            return;
        }

        JsonArray legacyFields = legacyFieldsJsonEl.getAsJsonArray();
        for (JsonElement field : legacyFields) {
            populateUserStudyLegacyData(handle, sourceData, field.getAsString(), studyId, participantId, instanceId);
        }
    }

    private void populateUserStudyLegacyData(Handle handle, JsonElement sourceData, String fieldName,
                                             long studyId, long participantId, Long instanceId) {

        JsonElement valueEl = sourceData.getAsJsonObject().get(fieldName);
        if (valueEl != null && !valueEl.isJsonNull() && StringUtils.isNotEmpty(valueEl.getAsString())) {
            LOG.debug(" study: {} .. userguid: {}  actinstanceguid: {} fieldName: {} fieldValue: {} ",
                    studyId, participantId, instanceId, fieldName, valueEl.getAsString());

            handle.attach(JdbiUserStudyLegacyData.class).insert(participantId, studyId, instanceId,
                    fieldName, valueEl.getAsString());
        }
    }

    private String processPicklistQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                           String participantGuid, String instanceGuid, AnswerDao answerDao) {

        String answerGuid = null;
        String questionName = getStringValueFromElement(mapElement, "name");
        String sourceType = getStringValueFromElement(mapElement, "source_type");
        //handle options
        String stableId = getStringValueFromElement(mapElement, "stable_id");

        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
        if (mapElement.getAsJsonObject().get("options") == null || mapElement.getAsJsonObject().get("options").isJsonNull()) {
            //this will handle "country" : "US"
            String value = getStringValueFromElement(sourceDataElement, questionName);
            if (StringUtils.isNotEmpty(value)) {
                selectedPicklistOptions.add(new SelectedPicklistOption(value));
            }
            sourceDataSurveyQs.get(surveyName).add(questionName);
        } else if (StringUtils.isNotEmpty(sourceType)) {
            //as of now only one data type integer other than string
            switch (sourceType) {
                case "string":
                    selectedPicklistOptions = getPicklistOptionsForSourceStrs(mapElement, sourceDataElement, questionName, surveyName);
                    break;
                case "integer":
                    //"currently_medicated": 1 //0/1/2 for N/Y/Dk
                    selectedPicklistOptions = getPicklistOptionsForSourceNumbers(mapElement, sourceDataElement, questionName, surveyName);
                    break;
                default:
                    LOG.warn("source type: {} not supported", sourceType, questionName);
            }
        } else {
            selectedPicklistOptions = getSelectedPicklistOptions(mapElement, sourceDataElement, questionName, surveyName);
        }
        if (CollectionUtils.isNotEmpty(selectedPicklistOptions)) {
            answerGuid = answerPickListQuestion(handle, stableId, participantGuid, instanceGuid, selectedPicklistOptions, answerDao);
        }
        return answerGuid;

    }

    private List<SelectedPicklistOption> getPicklistOptionsForSourceNumbers(JsonElement mapElement, JsonElement sourceDataElement,
                                                                            String questionName, String surveyName) {
        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
        JsonElement value = null;

        sourceDataSurveyQs.get(surveyName).add(questionName);

        if (sourceDataElement != null && !sourceDataElement.getAsJsonObject().get(questionName).isJsonNull()) {
            value = sourceDataElement.getAsJsonObject().get(questionName);
        }
        if (value == null || value.isJsonNull()) {
            return selectedPicklistOptions;
        }

        if (yesNoDkLookup.get(value.getAsInt()) != null) {
            selectedPicklistOptions.add(new SelectedPicklistOption(yesNoDkLookup.get(value.getAsInt())));
        }
        return selectedPicklistOptions;

    }


    private List<SelectedPicklistOption> getPicklistOptionsForSourceStrs(JsonElement mapElement, JsonElement sourceDataElement,
                                                                         String questionName, String surveyName) {
        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
        JsonElement value = null;

        sourceDataSurveyQs.get(surveyName).add(questionName);

        //check if source data doesnot have options. try for a match
        if (sourceDataElement != null && !sourceDataElement.getAsJsonObject().get(questionName).isJsonNull()) {
            value = sourceDataElement.getAsJsonObject().get(questionName);
        }
        if (value == null || value.isJsonNull()) {
            return selectedPicklistOptions;
        }

        //RACE has multiple values selected.
        //ex:- "American Indian or Native American, Japanese, Other, something else not on the list"
        //"something else not on the list" is other text / other details.
        //parse by `,` and check each value ..
        String[] optValues = value.getAsString().split(",");
        List<String> optValuesList = new ArrayList<>();
        optValuesList.addAll(Arrays.asList(optValues));
        optValuesList.replaceAll(String::trim);

        JsonArray options = mapElement.getAsJsonObject().getAsJsonArray("options");
        for (JsonElement option : options) {
            JsonElement optionNameEl = option.getAsJsonObject().get("name");
            String optionName = optionNameEl.getAsString();
            if (altNames.get(optionName) != null) {
                optionName = altNames.get(optionName);
            }
            final String optName = optionName;
            if (optionName.equalsIgnoreCase(value.getAsString())
                    || optValuesList.stream().anyMatch(x -> x.equalsIgnoreCase(optName))) {

                //todo.. handle other_text in a better way!
                if (optionName.equalsIgnoreCase("Other")) {
                    String otherDetails = null;
                    if (optValuesList.size() > (selectedPicklistOptions.size() + 1)) {
                        //there is Other text
                        otherDetails = optValuesList.get(optValuesList.size() - 1);
                    }
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionNameEl.getAsString().toUpperCase(), otherDetails));
                } else {
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionNameEl.getAsString().toUpperCase()));
                }
            }
        }

        return selectedPicklistOptions;
    }

    private List<SelectedPicklistOption> getSelectedPicklistOptions(JsonElement mapElement, JsonElement sourceDataElement,
                                                                    String questionName, String surveyName) {
        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();

        JsonArray options = mapElement.getAsJsonObject().getAsJsonArray("options");
        for (JsonElement option : options) {
            JsonElement value = null;
            JsonElement optionName = option.getAsJsonObject().get("name");
            String key;
            if (optionName != null && !optionName.isJsonNull()) {
                String optName = optionName.getAsString();
                if (altNames.get(optName) != null) {
                    optName = altNames.get(optName);
                }
                key = questionName + "." + optName;
            } else {
                key = questionName;
            }
            JsonElement sourceDataOptEl = sourceDataElement.getAsJsonObject().get(key);
            if (sourceDataOptEl != null && !sourceDataOptEl.isJsonNull()) {
                value = sourceDataElement.getAsJsonObject().get(key);
                sourceDataSurveyQs.get(surveyName).add(key);
            }
            if (value != null && value.getAsInt() == 1) { //option checked
                if (option.getAsJsonObject().get("text") != null) {
                    //other text details
                    String otherTextKey = key.concat(".").concat(option.getAsJsonObject().get("text").getAsString());
                    JsonElement otherTextEl = sourceDataElement.getAsJsonObject().get(otherTextKey);
                    String otherText = null;
                    if (otherTextEl != null && !otherTextEl.isJsonNull()) {
                        otherText = otherTextEl.getAsString();
                    }
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionName.getAsString().toUpperCase(), otherText));
                } else {
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionName.getAsString().toUpperCase()));
                }
            }
        }
        return selectedPicklistOptions;
    }

    private String processDateQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                       String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid = null;
        JsonElement valueEl;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }
        //check if question has subelements, nullable
        if (mapElement.getAsJsonObject().get("subelements") == null) {
            valueEl = sourceDataElement.getAsJsonObject().get(questionName);
            String sourceType = getStringValueFromElement(mapElement, "source_type");
            sourceDataSurveyQs.get(surveyName).add(questionName);
            //todo.. revisit below to handle month/day in addition to year for future studies !!
            if (valueEl != null && !valueEl.isJsonNull() && !StringUtils.isEmpty(valueEl.getAsString())) {
                if (sourceType != null && sourceType.equals("string")) {
                    String valueStr = valueEl.getAsString();
                    int valueInt = Integer.parseInt(valueStr);
                    DateValue dateValue = new DateValue(valueInt, null, null);
                    answerGuid = answerDateQuestion(handle, stableId, participantGuid, instanceGuid, dateValue, answerDao);
                } else {
                    String dateFormat = mapElement.getAsJsonObject().get("format").getAsString();
                    DateValue dateValue = parseDate(valueEl.getAsString(), dateFormat);
                    answerGuid = answerDateQuestion(handle, stableId, participantGuid, instanceGuid, dateValue, answerDao);
                }
            }
        } else {
            //handle subelements
            JsonArray subelements = mapElement.getAsJsonObject().getAsJsonArray("subelements");
            Map<String, JsonElement> dateSubelements = new HashMap<>();
            for (JsonElement subelement : subelements) {
                String subelementName = subelement.getAsJsonObject().get("name").getAsString();
                String key = questionName + "_" + subelementName;
                if (altNames.containsKey(key)) {
                    key = altNames.get(key);
                }
                valueEl = sourceDataElement.getAsJsonObject().get(key);
                dateSubelements.put(subelementName, valueEl);
                sourceDataSurveyQs.get(surveyName).add(key);
            }
            //build date
            Integer year = null;
            Integer month = null;
            Integer day = null;
            JsonElement yearEl = dateSubelements.get("year");
            JsonElement monthEl = dateSubelements.get("month");
            JsonElement dayEl = dateSubelements.get("day");
            if (yearEl != null && !yearEl.isJsonNull() && !yearEl.getAsString().isEmpty()) {
                year = yearEl.getAsInt();
            }
            if (monthEl != null && !monthEl.isJsonNull() && !monthEl.getAsString().isEmpty()) {
                month = monthEl.getAsInt();
            }
            if (dayEl != null && !dayEl.isJsonNull() && !dayEl.getAsString().isEmpty()) {
                day = dayEl.getAsInt();
            }
            DateValue dateValue = new DateValue(year, month, day);
            answerGuid = answerDateQuestion(handle, stableId, participantGuid, instanceGuid, dateValue, answerDao);
        }
        return answerGuid;
    }

    private String processTextQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                       String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid = null;
        JsonElement valueEl;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();

        valueEl = sourceDataElement.getAsJsonObject().get(questionName);
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }

        if (valueEl != null && !valueEl.isJsonNull()) {
            answerGuid = answerTextQuestion(handle, stableId, participantGuid, instanceGuid, valueEl.getAsString(), answerDao);
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);
        return answerGuid;
    }


    private String processAgreementQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                            String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid;
        String stableId = null;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName);
        if (valueEl == null || valueEl.isJsonNull()) {
            return null;
        }
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }
        if (stableId == null) {
            return null;
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);

        answerGuid = answerAgreementQuestion(handle, stableId, participantGuid, instanceGuid, valueEl.getAsBoolean(), answerDao);
        return answerGuid;
    }

    private String processCompositeQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                            String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid = null;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        sourceDataSurveyQs.get(surveyName).add(questionName);
        //handle composite options (nested answers)
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }
        //handle children/nestedQA
        JsonArray children = mapElement.getAsJsonObject().getAsJsonArray("children");
        List<String> nestedQAGuids = new ArrayList<>();
        List<Integer> nestedAnsOrders = new ArrayList<>();
        String childGuid;
        //todo.. This composite type does not handle array/list of answers. make it handle array for future study proof
        for (JsonElement childEl : children) {
            nestedAnsOrders.add(0);
            if (childEl != null && !childEl.isJsonNull()) {
                String nestedQuestionType = getStringValueFromElement(childEl, "type");
                switch (nestedQuestionType) {
                    case "Date":
                        childGuid = processDateQuestion(handle, childEl, sourceDataElement, surveyName, participantGuid,
                                instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "string":
                        childGuid = processTextQuestion(handle, childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Picklist":
                        childGuid = processPicklistQuestion(handle, childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Boolean":
                        childGuid = processBooleanQuestion(handle, childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Agreement":
                        childGuid = processAgreementQuestion(handle, childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "ClinicalTrialPicklist":
                        //todo .. revisit and make it generic
                        String childStableId = childEl.getAsJsonObject().get("stable_id").getAsString();
                        JsonElement thisDataArrayEl = sourceDataElement.getAsJsonObject().get(questionName);
                        if (thisDataArrayEl != null && !thisDataArrayEl.isJsonNull()) {
                            Boolean isClinicalTrial = getBooleanValueFromElement(thisDataArrayEl, "clinicaltrial");
                            //thisDataArrayEl.getAsJsonObject().get("clinicaltrial").getAsBoolean();
                            if (isClinicalTrial) {
                                List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
                                selectedPicklistOptions.add(new SelectedPicklistOption("IS_CLINICAL_TRIAL"));
                                childGuid = answerPickListQuestion(handle, childStableId, participantGuid,
                                        instanceGuid, selectedPicklistOptions, answerDao);
                                nestedQAGuids.add(childGuid);
                            }
                        }
                        break;
                    default:
                        LOG.warn(" Default ..Composite nested Q name: {} .. type: {} not supported", questionName,
                                nestedQuestionType);
                }
            }
        }
        nestedQAGuids.remove(null);
        if (CollectionUtils.isNotEmpty(nestedQAGuids)) {
            answerGuid = answerCompositeQuestion(handle, stableId, participantGuid, instanceGuid,
                    nestedQAGuids, nestedAnsOrders, answerDao);
        }

        return answerGuid;
    }

    private String processMedListCompositeQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                                   String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        //todo .. with some work processCompositeQuestion can be used to handle this question.
        //this handles composite question with list/array
        String answerGuid = null;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        sourceDataSurveyQs.get(surveyName).add(questionName);
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }

        //source data has array of composite data
        JsonElement dataArrayEl = sourceDataElement.getAsJsonObject().get(questionName);
        if (dataArrayEl != null && !dataArrayEl.isJsonNull()) {
            int compositeAnswerOrder = -1;
            List<String> nestedQAGuids = new ArrayList<>();
            List<Integer> nestedAnsOrders = new ArrayList<>();
            for (JsonElement thisDataEl : dataArrayEl.getAsJsonArray()) {
                compositeAnswerOrder++;
                //handle children/nestedQA
                JsonArray children = mapElement.getAsJsonObject().getAsJsonArray("children");
                String childGuid;
                for (JsonElement childEl : children) {
                    if (childEl != null && !childEl.isJsonNull()) {
                        String nestedQuestionType = getStringValueFromElement(childEl, "type");

                        switch (nestedQuestionType) {
                            case "Date":
                                childGuid = processDateQuestion(handle, childEl, thisDataEl, surveyName, participantGuid,
                                        instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "string":
                                childGuid = processTextQuestion(handle, childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Picklist":
                                childGuid = processPicklistQuestion(handle, childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Boolean":
                                childGuid = processBooleanQuestion(handle, childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Agreement":
                                childGuid = processAgreementQuestion(handle, childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "ClinicalTrialPicklist":
                                //todo .. revisit and make it generic
                                String childStableId = childEl.getAsJsonObject().get("stable_id").getAsString();
                                Boolean isClinicalTrial = thisDataEl.getAsJsonObject().get("clinicaltrial").getAsBoolean();
                                if (isClinicalTrial) {
                                    List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
                                    selectedPicklistOptions.add(new SelectedPicklistOption("IS_CLINICAL_TRIAL"));
                                    childGuid = answerPickListQuestion(handle, childStableId, participantGuid,
                                            instanceGuid, selectedPicklistOptions, answerDao);
                                    nestedQAGuids.add(childGuid);
                                    nestedAnsOrders.add(compositeAnswerOrder);
                                }
                                break;
                            default:
                                LOG.warn(" Default ..Composite nested Q name: {} .. type: {} ", questionName,
                                        nestedQuestionType);
                        }
                    }
                }
            }

            nestedQAGuids.remove(null);
            if (CollectionUtils.isNotEmpty(nestedQAGuids)) {
                answerGuid = answerCompositeQuestion(handle, stableId, participantGuid, instanceGuid, nestedQAGuids,
                        nestedAnsOrders, answerDao);
            }
        }

        return answerGuid;
    }


    private String processBooleanSpecialPLQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                                   String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        //in reality a Boolean question but ended up as a Picklist with just one option 'YES'
        //reason: boolean question does not support rendering as a checkbox
        //ex: "current_medication_names.dk": 0 ; "previous_medication_names.dk": 0,
        String answerGuid = null;
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }
        if (stableId == null) {
            return null;
        }
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName);
        if (valueEl == null || valueEl.isJsonNull()) {
            return null;
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);
        int valueInt = valueEl.getAsInt();
        List<SelectedPicklistOption> selectedOptions = new ArrayList<>();
        if (valueInt == 1) {
            selectedOptions.add(new SelectedPicklistOption("YES"));
        }

        //answerGuid = answerBooleanQuestion(handle, stableId, participantGuid, instanceGuid, value, answerDao);
        answerGuid = answerPickListQuestion(handle, stableId, participantGuid, instanceGuid, selectedOptions, answerDao);
        return answerGuid;
    }

    private String processBooleanQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                          String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid = null;
        String stableId = null;
        JsonElement stableIdElement = mapElement.getAsJsonObject().get("stable_id");
        if (!stableIdElement.isJsonNull()) {
            stableId = stableIdElement.getAsString();
        }
        if (stableId == null) {
            return null;
        }
        String questionName = getStringValueFromElement(mapElement, "name");
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName);
        if (valueEl == null || valueEl.isJsonNull()) {
            return null;
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);
        String sourceType = getStringValueFromElement(mapElement, "source_type");
        Boolean value = null;
        //below code needed to handle different variations of picklist boolean data
        //#1: "clinicaltrial": false
        //#2:"previous_medication_names.dk": 0,
        if (StringUtils.isNotEmpty(sourceType)) {
            //as of now only one data type integer other than string
            if (sourceType.equals("integer")) {
                value = booleanValueLookup.get(valueEl.getAsInt());
            } else {
                value = valueEl.getAsBoolean();
            }
        } else {
            LOG.error("NO source type for Boolean Q: {} ", stableId);
        }

        answerGuid = answerBooleanQuestion(handle, stableId, participantGuid, instanceGuid, value, answerDao);
        return answerGuid;
    }

    private void processPhysicianList(Handle handle, JsonElement sourceDataElement,
                                      UserDto userDto, StudyDto studyDto,
                                      String elementName, InstitutionType type, String surveyName,
                                      ActivityInstanceDto instanceDto) {

        MedicalProviderDao medicalProviderDao = handle.attach(MedicalProviderDao.class);
        sourceDataSurveyQs.get(surveyName).add(elementName);
        JsonElement medicalProviderDataEl = sourceDataElement.getAsJsonObject().get(elementName);
        if (medicalProviderDataEl == null || medicalProviderDataEl.isJsonNull()) {
            return;
        }
        long institutionTypeId = handle.attach(JdbiInstitutionType.class).findByType(InstitutionType.PHYSICIAN);
        List<MedicalProviderDto> dbPhysicianList = handle.attach(JdbiMedicalProvider.class)
                .getAllByUserGuidStudyGuidAndInstitutionTypeId(userDto.getUserGuid(), studyDto.getGuid(), institutionTypeId);

        JsonArray medicalProviderDataArray = medicalProviderDataEl.getAsJsonArray();
        for (JsonElement physicianEl : medicalProviderDataArray) {
            String physicianId = getStringValueFromElement(physicianEl, "physicianid");
            String name = getStringValueFromElement(physicianEl, "name");
            String institution = getStringValueFromElement(physicianEl, "institution");
            String city = getStringValueFromElement(physicianEl, "city");
            String state = getStringValueFromElement(physicianEl, "state");
            String postalCode = getStringValueFromElement(physicianEl, "zipcode");
            String phoneNumber = getStringValueFromElement(physicianEl, "phonenumber");
            String streetAddress = getStringValueFromElement(physicianEl, "streetaddress");

            //check if this physician already exists in DB.. probably populated by release survey for same ptp
            List<MedicalProviderDto> matchedPhysicianList = dbPhysicianList.stream().filter(physician ->
                    (physician.getInstitutionName() != null && physician.getInstitutionName().equalsIgnoreCase(institution))
                            && (physician.getPhysicianName() != null && physician.getPhysicianName().equalsIgnoreCase(name))
                            && (physician.getCity() != null && physician.getCity().equalsIgnoreCase(city))
                            && (physician.getState() != null && physician.getState().equalsIgnoreCase(state))
                            && (physician.getPhone() != null && physician.getPhone().equalsIgnoreCase(phoneNumber))
                            && (physician.getStreet() != null && physician.getStreet().equalsIgnoreCase(streetAddress)))
                    .collect(Collectors.toList());

            if (matchedPhysicianList.isEmpty()) {
                String guid = getMedicalProviderGuid(handle);
                medicalProviderDao.insert(new MedicalProviderDto(
                        null,
                        guid,
                        userDto.getUserId(),
                        studyDto.getId(),
                        type,
                        institution,
                        name,
                        city,
                        state,
                        postalCode,
                        phoneNumber,
                        physicianId,
                        streetAddress
                ));
            } else {
                LOG.warn("skipping duplicate physician: {} for participant: {}", physicianId, userDto.getUserGuid());
            }
        }
    }


    private void processInstitutions(Handle handle, JsonElement sourceDataElement, UserDto userDto, StudyDto studyDto,
                                     String elementName, InstitutionType type, String surveyName,
                                     ActivityInstanceDto instanceDto) {

        MedicalProviderDao medicalProviderDao = handle.attach(MedicalProviderDao.class);
        sourceDataSurveyQs.get(surveyName).add(elementName);
        JsonElement medicalProviderDataEl = sourceDataElement.getAsJsonObject().get(elementName);
        if (medicalProviderDataEl == null || medicalProviderDataEl.isJsonNull()) {
            return;
        }

        boolean isFirst = true;
        InstitutionType thisType = type;
        JsonArray medicalProviderDataArray = medicalProviderDataEl.getAsJsonArray();
        for (JsonElement physicianEl : medicalProviderDataArray) {
            String physicianId = getStringValueFromElement(physicianEl, "physicianid");
            String institutionId = getStringValueFromElement(physicianEl, "institutionid");
            String name = getStringValueFromElement(physicianEl, "name");
            String institution = getStringValueFromElement(physicianEl, "institution");
            String city = getStringValueFromElement(physicianEl, "city");
            String state = getStringValueFromElement(physicianEl, "state");
            String postalCode = getStringValueFromElement(physicianEl, "zipcode");
            String phoneNumber = getStringValueFromElement(physicianEl, "phonenumber");
            String streetAddress = getStringValueFromElement(physicianEl, "streetaddress");

            String guid = getMedicalProviderGuid(handle);
            String legacyGuid;
            if (InstitutionType.PHYSICIAN.equals(type)) {
                legacyGuid = physicianId;
            } else {
                legacyGuid = institutionId;
                if (isFirst) { //hack for MBC to load first one as Initial Biopsy and rest as Institutions
                    thisType = InstitutionType.INITIAL_BIOPSY;
                    isFirst = false;
                } else {
                    thisType = InstitutionType.INSTITUTION;
                }
            }

            medicalProviderDao.insert(new MedicalProviderDto(
                    null,
                    guid,
                    userDto.getUserId(),
                    studyDto.getId(),
                    thisType,
                    institution,
                    name,
                    city,
                    state,
                    postalCode,
                    phoneNumber,
                    legacyGuid,
                    streetAddress
            ));
        }
    }

    class UserExistsException extends Exception {
        UserExistsException(String msg) {
            super(msg);
        }
    }

    public void verifySourceQsLookedAt(String surveyName, JsonElement sourceDataEl) {
        List<String> mappingQuestions = sourceDataSurveyQs.get(surveyName);
        List<String> exportDataQs = new ArrayList<>();

        if (sourceDataEl == null || sourceDataEl.isJsonNull()) {
            LOG.warn(" Survey : {} null ", surveyName);
            return;
        }
        int actualQsCount = sourceDataEl.getAsJsonObject().entrySet().size();
        Set<Map.Entry<String, JsonElement>> actualQs = sourceDataEl.getAsJsonObject().entrySet();
        LOG.info("survey Name: {}  .. Qs looked at count: {} ... Actual source Qs: {} ", surveyName,
                mappingQuestions.size(), actualQsCount);
        for (String question : mappingQuestions) {
            LOG.debug(" Mapping Question: {} ", question);
        }

        for (Map.Entry<String, JsonElement> entry : actualQs) {
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
            if (!mappingQuestions.contains(entry.getKey())) {
                LOG.warn("*Question : \"{}\" missing from Mapping File", entry.getKey());
            }
            exportDataQs.add(entry.getKey());
        }

        for (String question : mappingQuestions) {
            if (!exportDataQs.contains(question)) {
                LOG.warn("*Mapping Question : \"{}\" not in source data ", question);
            }
        }
    }

    private DateValue parseDate(String dateValue, String fmt) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(fmt);
        Date consentDOB = dateFormat.parse(dateValue);
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.setTime(consentDOB);
        return new DateValue(gregorianCalendar.get(Calendar.YEAR),
                gregorianCalendar.get(Calendar.MONTH) + 1, // GregorianCalendar months are 0 indexed
                gregorianCalendar.get(Calendar.DAY_OF_MONTH));
    }

}
