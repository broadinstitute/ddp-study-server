
package org.broadinstitute.ddp.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants.MedicalProviderTable;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCountrySubnationalDivision;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.migration.BaseSurvey;
import org.broadinstitute.ddp.model.migration.SurveyAddress;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.time.DateUtils.MILLIS_PER_SECOND;

public class StudyDataLoaderAT {
    public static final String OTHER = "OTHER";
    //public static final String NED = "NED";
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String DK = "DK";
    private static final Logger LOG = LoggerFactory.getLogger(StudyDataLoaderAT.class);
    private static final String DEFAULT_PREFERRED_LANGUAGE_CODE = "en";
    private static final String DATSTAT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final String MH_DATE_FORMAT = "M/d/yy H:m[:s][a]";
    //private static final String MH_DATE_FORMAT = "M[M]/d[d]/yyyy h[h]:mm:ss a";
    private static final String DATSTAT_DATE_OF_BIRTH_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final int DSM_DEFAULT_ON_DEMAND_TRIGGER_ID = -2;
    private Long defaultKitCreationEpoch = null;

    public DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(MH_DATE_FORMAT)
            //.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            //.optionalStart()
            //.appendPattern(".ss")
            //.appendFraction(ChronoField.SECOND_OF_MINUTE, 0, 2, false)
            //.optionalEnd()
            .toFormatter();

    Map<String, List<String>> sourceDataSurveyQs;
    Map<String, String> altNames;
    Map<String, String> dkAltNames;
    Map<Integer, String> yesNoDkLookup;
    Map<Integer, Boolean> booleanValueLookup;
    Map<String, List<String>> datStatLookup;
    Auth0Util auth0Util;
    String auth0Domain;
    String mgmtToken;

    public StudyDataLoaderAT(Config cfg) {

        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);

        auth0Util = new Auth0Util(auth0Domain);
        var mgmtClient = new Auth0ManagementClient(
                auth0Domain,
                auth0Config.getString("managementApiClientId"),
                auth0Config.getString("managementApiSecret"));
        //mgmtToken = mgmtClient.getToken();

        sourceDataSurveyQs = new HashMap<>();

        //some lookup codes/values
        yesNoDkLookup = new HashMap<>();
        yesNoDkLookup.put(0, "NO");
        yesNoDkLookup.put(1, "YES");
        yesNoDkLookup.put(2, "DONT_KNOW");
        yesNoDkLookup.put(-1, "DONT_KNOW");

        datStatLookup = new HashMap<>();

        List<String> optionList = new ArrayList<>(List.of(
                "",
                "INDEPENDENTLY",
                "MOST_OF_THE_TIME",
                "WITH_ASSISTANCE",
                "USES_WALKER",
                "WHEELCHAIR_WITHOUT_ASSISTANCE",
                "WHEELCHAIR_WITH_ASSISTANCE"));
        datStatLookup.put("ambulation", optionList);


        optionList = new ArrayList<>(List.of(
                "",
                "HAS_CANCER_AND_NO_LONGER_TREATMENT",
                "HAS_CANCER_AND_TREATMENT",
                "REMISSION_AND_TREATMENT",
                "CANCER_HAS_RECENTLY_RECURRED",
                "REMISSION_AND_NO_LONGER_TREATMENT"));
        datStatLookup.put("cancer_status", optionList);

        optionList = new ArrayList<>(List.of(
                "",
                "AFRICAN_AFRICAN_AMERICAN",
                "LATINO",
                "EAST_ASIAN",
                "FINNISH",
                "NON-FINNISH_EUROPEAN",
                "CAUCASIAN",
                "SOUTH_ASIAN",
                "OTHER",
                "PREFER NOT TO ANSWER"));
        datStatLookup.put("ethnicity", optionList);

        optionList = new ArrayList<>(List.of(
                "",
                "INCONTINENCE_OCCASIONAL",
                "INCONTINENCE_FREQUENT"));
        datStatLookup.put("incontinence_type", optionList);

        optionList = List.of(
                "",
                "IGA_DEFICIENCY",
                "IVIG",
                "SUB_IG",
                "ANTIBIOTICS",
                "NO_TREATMENT"
        );
        datStatLookup.put("immunodeficiency_type", optionList);

        booleanValueLookup = new HashMap<>();
        booleanValueLookup.put(0, false);
        booleanValueLookup.put(1, true);

        dkAltNames = new HashMap<>();
        dkAltNames.put("dk", "Don't know");

        altNames = new HashMap<>();
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
            if (StringUtils.isBlank(firstName)) {
                firstName = "";
            }
            if (StringUtils.isBlank(lastName)) {
                lastName = "";
            }
            dao.insertByStudyGuidIfNotStoredAlready(firstName, lastName, email, studyCode, null, dateCreatedMillis);
        }
    }

    public ActivityInstanceDto createActivityInstance(JsonElement surveyData, String participantGuid,
                                                      long studyId, String activityCode, String createdAt,
                                                      JdbiActivity jdbiActivity,
                                                      ActivityInstanceDao activityInstanceDao,
                                                      ActivityInstanceStatusDao activityInstanceStatusDao) throws Exception {

        return createActivityInstance(surveyData, participantGuid, studyId, activityCode, createdAt,
                jdbiActivity, activityInstanceDao, activityInstanceStatusDao, false);
    }

    public ActivityInstanceDto createActivityInstance(JsonElement surveyData, String participantGuid,
                                                      long studyId, String activityCode, String createdAt,
                                                      JdbiActivity jdbiActivity,
                                                      ActivityInstanceDao activityInstanceDao,
                                                      ActivityInstanceStatusDao activityInstanceStatusDao,
                                                      boolean hideInstance) throws Exception {

        BaseSurvey baseSurvey = getBaseSurveyForActivity(surveyData, activityCode);
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
        if (ddpLastUpdated == null) {
            ddpLastUpdated = ddpCompleted;
        }
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
                LocalDateTime ddpCreatedAtTime = LocalDateTime.parse(ddpCreated, formatter);
                instant = ddpCreatedAtTime.toInstant(ZoneOffset.UTC);
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
                LocalDateTime ddpLastUpdatedTime = LocalDateTime
                        .parse(ddpLastUpdated, formatter);
                instant = ddpLastUpdatedTime.toInstant(ZoneOffset.UTC);
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
                LocalDateTime ddpCompletedTime = LocalDateTime
                        .parse(ddpLastUpdated, formatter);
                instant = ddpCompletedTime.toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                throw new Exception("Could not parse required completedAt value for " + activityCode
                        + " survey, value is " + ddpCompleted);
            }
            ddpCompletedAt = instant.toEpochMilli() + 1;
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
        InstanceStatusType instanceCurrentStatus = InstanceStatusType.COMPLETE;
        ActivityInstanceDto dto = activityInstanceDao
                .insertInstance(studyActivityId, participantGuid, participantGuid, InstanceStatusType.CREATED,
                        true,
                        ddpCreatedAt,
                        submissionId, sessionId, activityVersion, hideInstance);
        activityInstanceDao.updateIsHiddenByActivityInstance(dto.getId(), true);

        LOG.info("Created activity instance {} for activity {} and user {}",
                dto.getGuid(), activityCode, participantGuid);

        long activityInstanceId = dto.getId();
        activityInstanceStatusDao.insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, ddpCompletedAt, participantGuid);

        return dto;
    }

    public ActivityInstanceDto createActivityInstanceAT(JsonElement surveyData, String participantGuid,
                                                      long studyId, String activityCode, long createdAt, long completedAt,
                                                      JdbiActivity jdbiActivity,
                                                      ActivityInstanceDao activityInstanceDao,
                                                      ActivityInstanceStatusDao activityInstanceStatusDao,
                                                      boolean hideInstance) throws Exception {

        BaseSurvey baseSurvey = getBaseSurveyForActivity(surveyData, activityCode);

        Long submissionId = baseSurvey.getDatstatSubmissionId();
        String sessionId = baseSurvey.getDatstatSessionId();
        String activityVersion = baseSurvey.getActivityVersion();
        if (StringUtils.isEmpty(activityVersion)) {
            activityVersion = baseSurvey.getSurveyVersion();
        }
        Integer submissionStatus = baseSurvey.getDatstatSubmissionStatus();
        Long studyActivityId = jdbiActivity.findIdByStudyIdAndCode(studyId, activityCode).get();
        Long ddpLastUpdatedAt = completedAt;
        Long ddpCreatedAt = createdAt;
        Long ddpCompletedAt = completedAt;

        if (ddpLastUpdatedAt < ddpCreatedAt) {
            throw new Exception("Invalid ddpCreatedAt - ddpLastUpdated dates. created date : " + ddpCreatedAt
                    + " is greater than last updated date: " + ddpLastUpdatedAt + " in activity: " + activityCode
                    + " userguid: " + participantGuid);
        }

        String surveyStatus = baseSurvey.getSurveyStatus();
        InstanceStatusType instanceCurrentStatus = InstanceStatusType.COMPLETE;
        ActivityInstanceDto dto = activityInstanceDao
                .insertInstance(studyActivityId, participantGuid, participantGuid, InstanceStatusType.CREATED,
                        true,
                        ddpCreatedAt,
                        submissionId, sessionId, activityVersion, hideInstance);
        activityInstanceDao.updateIsHiddenByActivityInstance(dto.getId(), true);

        LOG.info("Created activity instance {} for activity {} and user {}",
                dto.getGuid(), activityCode, participantGuid);

        long activityInstanceId = dto.getId();
        activityInstanceStatusDao.insertStatus(activityInstanceId, InstanceStatusType.COMPLETE, ddpCompletedAt, participantGuid);

        return dto;
    }

    public void loadMedicalHistorySurveyData(Handle handle,
                                             JsonElement surveyData,
                                             JsonElement mappingData,
                                             StudyDto studyDto,
                                             UserDto userDto,
                                             ActivityInstanceDto instanceDto,
                                             AnswerDao answerDao) throws Exception {

        //LOG.info("Populating MedicalHistory Survey...");
        if (surveyData == null || surveyData.isJsonNull()) {
            LOG.warn("NO MedicalHistory Survey !");
            return;
        }

        processSurveyData(handle, "medicalhistorysurvey", surveyData, mappingData,
                studyDto, userDto, instanceDto, answerDao);
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

    String getMedicalProviderGuid(Handle handle) {
        return DBUtils.uniqueStandardGuid(handle,
                MedicalProviderTable.TABLE_NAME, MedicalProviderTable.MEDICAL_PROVIDER_GUID);
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

    private BaseSurvey getBaseSurveyForActivity(JsonElement surveyData, String activityCode) {
        String status = InstanceStatusType.COMPLETE.name();
        return getBaseSurvey(surveyData, status);
    }

    private BaseSurvey getBaseSurvey(JsonElement surveyData, String surveyStatus) {

        Integer datstatSubmissionIdNum = getIntegerValueFromElement(surveyData, "datstat.submissionid");
        Long datstatSubmissionId = null;
        if (datstatSubmissionIdNum != null) {
            datstatSubmissionId = datstatSubmissionIdNum.longValue();
        }
        String datstatSessionId = getStringValueFromElement(surveyData, "datstat.sessionid");
        String ddpCreated = getStringValueFromElement(surveyData, "ddp_created");
        String ddpFirstCompleted = getStringValueFromElement(surveyData, "ddp_firstcompleted");
        String ddpLastSubmitted = getStringValueFromElement(surveyData, "datstat_lastmodified") == null
                ? getStringValueFromElement(surveyData, "datstat.enddatetime") :
                getStringValueFromElement(surveyData, "datstat_lastmodified");
        String ddpLastUpdated = getStringValueFromElement(surveyData, "datstat.enddatetime") == null
                ? getStringValueFromElement(surveyData, "datstat_lastmodified")
                : getStringValueFromElement(surveyData, "datstat.enddatetime");
        String surveyVersion = getStringValueFromElement(surveyData, "surveyversion");
        String activityVersion = getStringValueFromElement(surveyData, "consent_version");
        Integer datstatSubmissionStatus = getIntegerValueFromElement(surveyData, "datstat.submissionstatus");

        if (ddpCreated == null) {
            ddpCreated = getStringValueFromElement(surveyData, "datstat.startdatetime"); //medical_history
        }

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
                //non question-answer-stable elements. continue to next element
                sourceDataSurveyQs.get(surveyName).add(questionName);
                continue;
            }

            //Now try to get source data for this question
            //check type and act accordingly
            switch (questionType) {
                case "Date":
                    processDateQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "string":
                    processTextQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Numeric":
                    processNumericQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Picklist":
                    processPicklistQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                //case "YesNoDkPicklist":
                //    processYesNoDkPicklistQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                //    break; //todo
                case "Boolean":
                    processBooleanQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "BooleanSpecialPL":
                    processBooleanSpecialPLQuestion(handle, thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Agreement":
                    processAgreementQuestion(thisMap, sourceData, surveyName, participantGuid, instanceGuid, answerDao);
                    break;
                case "Composite":
                    processCompositeQuestion(handle, thisMap, sourceData, surveyName,
                            participantGuid, instanceGuid, answerDao);
                    break;
                case "CompositeMedList":
                    processMedListCompositeQuestion(handle, thisMap, sourceData, surveyName,
                            participantGuid, instanceGuid, answerDao);
                    break;

                case "CompositeMedical":
                    processMedicalCompositeQuestion(handle, thisMap, sourceData, surveyName,
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

    private String processPicklistQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
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
                if (questionName.equals("datstat_physicalstate")) {
                    stableId = value.substring(0, 2) + "_" + stableId;
                }
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
            //LOG.info("---PL QStableID: {} .. selected options: {}", stableId, selectedPicklistOptions);
            answerGuid = answerPickListQuestion(stableId, participantGuid, instanceGuid, selectedPicklistOptions, answerDao);
        }
        return answerGuid;

    }

    private List<SelectedPicklistOption> getPicklistOptionsForSourceNumbers(JsonElement mapElement, JsonElement sourceDataElement,
                                                                            String questionName, String surveyName) {
        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
        JsonElement value = null;

        sourceDataSurveyQs.get(surveyName).add(questionName);

        if (sourceDataElement != null && sourceDataElement.getAsJsonObject().get(questionName) != null
                && !sourceDataElement.getAsJsonObject().get(questionName).isJsonNull()) {
            value = sourceDataElement.getAsJsonObject().get(questionName);
        }
        if (value == null || value.isJsonNull()) {
            return selectedPicklistOptions;
        }

        boolean foundValue = false;
        String val = null;
        if (datStatLookup.get(questionName) != null && datStatLookup.get(questionName).get(value.getAsInt()) != null) {
            foundValue = true;
            val = datStatLookup.get(questionName).get(value.getAsInt());
        } else if (yesNoDkLookup.get(value.getAsInt()) != null) {
            foundValue = true;
            val = yesNoDkLookup.get(value.getAsInt());
        }

        if (foundValue) {
            boolean foundSpecify = false;
            JsonArray options = mapElement.getAsJsonObject().getAsJsonArray("options");
            for (JsonElement option : options) {
                JsonObject optionObject = option.getAsJsonObject();
                JsonElement optionNameEl = optionObject.get("stable_id");
                String optionName = optionNameEl == null ? val : optionNameEl.getAsString();

                if (optionName != null
                        && !optionName.isEmpty() && val.equals(optionName)) {
                    JsonElement specifyKeyElement = optionObject.get("text");

                    if (specifyKeyElement != null && !specifyKeyElement.isJsonNull()
                            && !specifyKeyElement.getAsString().isEmpty()
                            && StringUtils.isNotEmpty(specifyKeyElement.getAsString())) {
                        foundSpecify = true;
                        String otherText = specifyKeyElement.getAsString();
                        LOG.debug("-----has specify: {} .. optionName:{}", otherText, optionName);
                        selectedPicklistOptions
                                .add(new SelectedPicklistOption(val, getStringValueFromElement(sourceDataElement, questionName + "."
                                        + otherText)));
                    }
                }
            }
            if (!foundSpecify) {
                selectedPicklistOptions.add(new SelectedPicklistOption(val));
            }

        }

        return selectedPicklistOptions;

    }


    private List<SelectedPicklistOption> getPicklistOptionsForSourceStrs(JsonElement mapElement, JsonElement sourceDataElement,
                                                                         String questionName, String surveyName) {
        List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
        JsonElement value = null;

        sourceDataSurveyQs.get(surveyName).add(questionName);
        //check if source data doesnot have options. try for a match
        if (sourceDataElement != null && sourceDataElement.getAsJsonObject().has(questionName)) {
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

        if (questionName.equals("DATSTAT_PHYSICALCOUNTRY") || questionName.equals("DATSTAT_PHYSICALSTATE")) {
            selectedPicklistOptions.add(new SelectedPicklistOption(value.getAsString().toUpperCase()));
            return selectedPicklistOptions;
        }

        List<String> pepperPLOptions = new ArrayList<>();
        JsonArray options = mapElement.getAsJsonObject().getAsJsonArray("options");
        for (JsonElement option : options) {
            JsonObject optionObj = option.getAsJsonObject();
            JsonElement optionNameEl = optionObj.get("name");
            String optionName = optionNameEl.getAsString();
            if (altNames.get(optionName) != null) {
                optionName = altNames.get(optionName);
            } else if (dkAltNames.get(optionName) != null) {
                optionName = dkAltNames.get(optionName);
            }
            pepperPLOptions.add(optionName.toUpperCase());
            final String optName = optionName;
            if (optionName.equalsIgnoreCase(value.getAsString())
                    || optValuesList.stream().anyMatch(x -> x.equalsIgnoreCase(optName))) {

                //todo.. handle other_text in a better way!
                if (optionName.contains("other") || optionName.contains("telangiectasia")
                        || optionName.contains("allergies") || optionName.contains("high_cholesterol")
                        || optionName.contains("liver_issues") || optionName.contains("renal_issues")
                        || optionName.contains("thyroid_issues") || optionName.contains("g_tube")
                        || optionName.contains("eye") || optionName.contains("heel_cord")
                        || optionName.contains("rod_placement")) {
                    String otherDetails = null;
                    //if (optValuesList.size() > (selectedPicklistOptions.size() + 1)) {
                    //there is Other text
                    //otherDetails = optValuesList.get(optValuesList.size() - 1);
                    //}
                    if (optionObj.has("text") && !sourceDataElement.getAsJsonObject()
                            .get(questionName + "." + optionName + "." + optionObj.get("text").getAsString()).isJsonNull()) {
                        otherDetails = sourceDataElement.getAsJsonObject().get(questionName + "." + optionName + "." + optionObj
                                .get("text").getAsString()).getAsString();
                    }
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionNameEl.getAsString().toUpperCase(), otherDetails));
                } else {
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionNameEl.getAsString().toUpperCase()));
                }
            }
        }

        if ("RACE".equalsIgnoreCase(questionName)) {
            //Gen2 MBC has other details without user selecting "Other"
            //handle others by adding everything that doesn't match pepper options
            List<String> otherText = optValuesList.stream().filter(opt -> !pepperPLOptions.contains(opt.toUpperCase())).collect(toList());
            otherText.remove("Other");
            String otherDetails = otherText.stream().collect(Collectors.joining(","));
            if (StringUtils.isNotBlank(otherDetails)) {
                selectedPicklistOptions.add(new SelectedPicklistOption(OTHER, otherDetails));
            } else if (optValuesList.contains("Other")) {
                selectedPicklistOptions.add(new SelectedPicklistOption(OTHER));
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
            String optName = null;
            if (optionName != null && !optionName.isJsonNull()) {
                optName = optionName.getAsString();
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
            if (value != null && !value.isJsonNull() && !value.getAsString().isEmpty() && (key.contains("medication")
                    || key.contains("sibling"))) {
                int intValue = value.getAsInt();
                if (intValue == -1) {
                    intValue = 2;
                }
                selectedPicklistOptions
                        .add(new SelectedPicklistOption(options.get(intValue).getAsJsonObject().get("stable_id").getAsString()));
                break;
            } else if (value != null && !value.getAsString().isEmpty() && key.contains("sample_") && key.contains("_type")) {
                int intValue = value.getAsInt() - 1;
                selectedPicklistOptions
                        .add(new SelectedPicklistOption(options.get(intValue).getAsJsonObject().get("stable_id").getAsString()));
                break;
            } else if (value != null && !value.getAsString().isEmpty() && value.getAsInt() == 1) { //option checked
                if (option.getAsJsonObject().get("text") != null) {
                    //other text details
                    String otherText = getTextDetails(sourceDataElement, option, key);
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionName.getAsString().toUpperCase(), otherText));
                } else {
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionName.getAsString().toUpperCase()));
                }
            } else if ("Other".equalsIgnoreCase(optName) && option.getAsJsonObject().get("text") != null) {
                //additional check to handle scenarios where:
                //Other is NOT checked but other_text details are entered
                String otherText = getTextDetails(sourceDataElement, option, key);
                if (StringUtils.isNotBlank(otherText)) {
                    //other text details
                    selectedPicklistOptions.add(new SelectedPicklistOption(optionName.getAsString().toUpperCase(), otherText));
                }
            }
        }
        return selectedPicklistOptions;
    }

    private String getTextDetails(JsonElement sourceDataElement, JsonElement option, String key) {
        String otherTextKey = key.concat(".").concat(option.getAsJsonObject().get("text").getAsString());
        JsonElement otherTextEl = sourceDataElement.getAsJsonObject().get(otherTextKey);
        String otherText = null;
        if (otherTextEl != null && !otherTextEl.isJsonNull()) {
            otherText = otherTextEl.getAsString();
        }
        return otherText;
    }

    private String processDateQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
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
                    answerGuid = answerDateQuestion(stableId, participantGuid, instanceGuid, dateValue, answerDao);
                } else {
                    String dateFormat = mapElement.getAsJsonObject().get("format").getAsString();
                    DateValue dateValue = parseDate(valueEl.getAsString(), dateFormat);
                    answerGuid = answerDateQuestion(stableId, participantGuid, instanceGuid, dateValue, answerDao);
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
            answerGuid = answerDateQuestion(stableId, participantGuid, instanceGuid, dateValue, answerDao);
        }
        return answerGuid;
    }

    private String processTextQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
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

        if (valueEl != null && !valueEl.isJsonNull() && !valueEl.getAsString().isEmpty()) {
            answerGuid = answerTextQuestion(stableId, participantGuid, instanceGuid, valueEl.getAsString(), answerDao);
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);
        return answerGuid;
    }

    private String processNumericQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
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
            try {
                answerGuid = answerNumericQuestion(stableId, participantGuid, instanceGuid, valueEl.getAsLong(), answerDao);
            } catch (Exception e) {
                answerGuid = answerNumericQuestion(stableId, participantGuid, instanceGuid, null, answerDao);
            }
        }
        sourceDataSurveyQs.get(surveyName).add(questionName);
        return answerGuid;
    }


    private String processAgreementQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                            String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        String answerGuid;
        String stableId = null;
        String questionName = mapElement.getAsJsonObject().get("name").getAsString();
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName.toUpperCase());
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

        answerGuid = answerAgreementQuestion(stableId, participantGuid, instanceGuid, valueEl.getAsBoolean(), answerDao);
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
                        childGuid = processDateQuestion(childEl, sourceDataElement, surveyName, participantGuid,
                                instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "string":
                        childGuid = processTextQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Numeric":
                        childGuid = processNumericQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Picklist":
                        childGuid = processPicklistQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Boolean":
                        childGuid = processBooleanQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        break;
                    case "Agreement":
                        childGuid = processAgreementQuestion(childEl, sourceDataElement, surveyName,
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
                                childGuid = answerPickListQuestion(childStableId, participantGuid,
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
        nestedQAGuids.removeAll(Collections.singleton(null));
        if (CollectionUtils.isNotEmpty(nestedQAGuids)) {
            answerGuid = answerCompositeQuestion(handle, stableId, participantGuid, instanceGuid,
                    nestedQAGuids, nestedAnsOrders, answerDao);
        }
        return answerGuid;
    }

    public String answerDateQuestion(String pepperQuestionStableId, String participantGuid, String instanceGuid,
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

    public String answerTextQuestion(String pepperQuestionStableId,
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

    public String answerNumericQuestion(String pepperQuestionStableId,
                                        String participantGuid,
                                        String instanceGuid,
                                        Long value, AnswerDao answerDao) {
        String guid = null;
        if (value != null) {
            Answer answer = new NumericAnswer(null, pepperQuestionStableId, null, value);
            guid = answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return guid;
    }


    public String answerBooleanQuestion(String pepperQuestionStableId,
                                        String participantGuid,
                                        String instanceGuid,
                                        Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new BoolAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public String answerAgreementQuestion(String pepperQuestionStableId,
                                          String participantGuid,
                                          String instanceGuid,
                                          Boolean value, AnswerDao answerDao) throws Exception {
        if (value != null) {
            Answer answer = new AgreementAnswer(null, pepperQuestionStableId, null, value);
            return answerDao.createAnswer(participantGuid, instanceGuid, answer).getAnswerGuid();
        }
        return null;
    }

    public String answerPickListQuestion(String questionStableId, String participantGuid, String instanceGuid,
                                         List<SelectedPicklistOption> selectedPicklistOptions, AnswerDao answerDao) {
        //LOG.info("---PL QSID: {}", questionStableId);
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
                if (childGuid != null) {
                    Long childAnswerId = answerDao.getAnswerSql().findDtoByGuid(childGuid).get().getId();
                    childrenAnswerIds.add(childAnswerId);
                }
            }
            //LOG.debug("--------chd answers: {} orderIds : {} ", childrenAnswerIds.size(), compositeAnswerOrders.size());
            if (childrenAnswerIds.size() != compositeAnswerOrders.size()) {
                //LOG.info("----------Not equal----STBLID: {} ", pepperQuestionStableId);
            } else {
                //LOG.info("----------EQUAL----");
            }
            jdbiCompositeAnswer.insertChildAnswerItems(parentAnswer.getAnswerId(), childrenAnswerIds, compositeAnswerOrders);
        }
        return parentAnswer.getAnswerGuid();
    }

    private String processMedicalCompositeQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                                   String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        LOG.debug("---processMedicalCompositeQuestion------");
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
        for (JsonElement childEl : children) {
            if (childEl != null && !childEl.isJsonNull()) {
                Integer childOrder = null;

                String nestedQuestionType = getStringValueFromElement(childEl, "type");
                switch (nestedQuestionType) {
                    case "Date":
                        childGuid = processDateQuestion(childEl, sourceDataElement, surveyName, participantGuid,
                                instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "string":
                        childGuid = processTextQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "Numeric":
                        childGuid = processNumericQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "Picklist":
                        childGuid = processPicklistQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "Boolean":
                        childGuid = processBooleanQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "Agreement":
                        childGuid = processAgreementQuestion(childEl, sourceDataElement, surveyName,
                                participantGuid, instanceGuid, answerDao);
                        nestedQAGuids.add(childGuid);
                        if (childGuid != null) {
                            childOrder = childEl.getAsJsonObject().get("response_order").getAsInt();
                        }
                        break;
                    case "ClinicalTrialPicklist":
                        //todo .. revisit and make it generic
                        LOG.info("-------ClinicalTrialPicklist------");
                        String childStableId = childEl.getAsJsonObject().get("stable_id").getAsString();
                        JsonElement thisDataArrayEl = sourceDataElement.getAsJsonObject().get(questionName);
                        if (thisDataArrayEl != null && !thisDataArrayEl.isJsonNull()) {
                            Boolean isClinicalTrial = getBooleanValueFromElement(thisDataArrayEl, "clinicaltrial");
                            //thisDataArrayEl.getAsJsonObject().get("clinicaltrial").getAsBoolean();
                            if (isClinicalTrial) {
                                List<SelectedPicklistOption> selectedPicklistOptions = new ArrayList<>();
                                selectedPicklistOptions.add(new SelectedPicklistOption("IS_CLINICAL_TRIAL"));
                                childGuid = answerPickListQuestion(childStableId, participantGuid,
                                        instanceGuid, selectedPicklistOptions, answerDao);
                                nestedQAGuids.add(childGuid);
                            }
                        }
                        break;
                    default:
                        LOG.warn(" Default ..Composite nested Q name: {} .. type: {} not supported", questionName,
                                nestedQuestionType);
                }
                nestedAnsOrders.add(childOrder);
            }
        }
        nestedQAGuids.removeAll(Collections.singleton(null));
        nestedAnsOrders.removeAll(Collections.singleton(null));
        nestedQAGuids.removeAll(Collections.singleton(""));
        nestedAnsOrders.removeAll(Collections.singleton(""));
        List<String> filteredQAGuids = nestedQAGuids.stream().filter(value ->
                value != null && value.length() > 0
        ).collect(Collectors.toList());


        LOG.debug("--------nested GUIDs in processMED sableID: {} .. : {} .. nestedAnswerOrders: {} .. filteredQAGUids: {}",
                stableId, nestedQAGuids.size(), nestedAnsOrders.size(), filteredQAGuids.size());

        if (CollectionUtils.isNotEmpty(nestedQAGuids)) {
            answerGuid = answerCompositeQuestion(handle, stableId, participantGuid, instanceGuid,
                    nestedQAGuids, nestedAnsOrders, answerDao);
        }
        return answerGuid;
    }

    private String processMedListCompositeQuestion(Handle handle, JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
                                                   String participantGuid, String instanceGuid, AnswerDao answerDao) throws Exception {

        LOG.info("******processMedListCompositeQuestion*****");
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
                                childGuid = processDateQuestion(childEl, thisDataEl, surveyName, participantGuid,
                                        instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "string":
                                childGuid = processTextQuestion(childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Picklist":
                                childGuid = processPicklistQuestion(childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Boolean":
                                childGuid = processBooleanQuestion(childEl, thisDataEl, surveyName,
                                        participantGuid, instanceGuid, answerDao);
                                nestedQAGuids.add(childGuid);
                                nestedAnsOrders.add(compositeAnswerOrder);
                                break;
                            case "Agreement":
                                childGuid = processAgreementQuestion(childEl, thisDataEl, surveyName,
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
                                    childGuid = answerPickListQuestion(childStableId, participantGuid,
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

            LOG.info("--------nested GUIDs : {}", nestedQAGuids.size());
            nestedQAGuids.remove(null);
            LOG.info("--------nested GUIDs in processMED : {} .. nestedAnswerOrders: {}", nestedQAGuids.size(), nestedAnsOrders.size());
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
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName.toUpperCase());
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
        answerGuid = answerPickListQuestion(stableId, participantGuid, instanceGuid, selectedOptions, answerDao);
        return answerGuid;
    }

    private String processBooleanQuestion(JsonElement mapElement, JsonElement sourceDataElement, String surveyName,
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
        JsonElement valueEl = sourceDataElement.getAsJsonObject().get(questionName.toUpperCase());
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

        answerGuid = answerBooleanQuestion(stableId, participantGuid, instanceGuid, value, answerDao);
        return answerGuid;
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


