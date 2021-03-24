package org.broadinstitute.ddp.datstat;

import com.easypost.EasyPost;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gson.*;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.email.EmailClient;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.exception.*;
import org.broadinstitute.ddp.handlers.AbstractRequestHandler;
import org.broadinstitute.ddp.handlers.FollowUpSurveyHandler;
import org.broadinstitute.ddp.handlers.util.*;
import org.broadinstitute.ddp.user.BasicUser;
import org.broadinstitute.ddp.util.*;
import org.quartz.*;
import org.quartz.impl.matchers.KeyMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * This class does all the DatStat related work except for the generation of authentication tokens
 * which is handled by AuthSingleton.
 */
public class DatStatUtil implements EDCClient
{
    private static final Logger logger = LoggerFactory.getLogger(DatStatUtil.class);

    private static final String LOG_PREFIX = "DATSTAT CLIENT - ";

    public static final String SKIP_ADDRESS_VALIDATION = "SKIP_ADDRESS_VALIDATION";

    public static final String ALTPID_FIELD = "DATSTAT_ALTPID";

    private static final String INVALID_ADDRESS_MESSAGE = "<p>An invalid address has been detected for participant with shortId: <b>%s</b></p>";



    public static final String PATH_API = "/designerservice/api";
    public static final String PATH_GET_PARTICIPANT_LISTS = "/participantlists";
    public static final String PATH_POST_PARTICIPANT_UPDATE = "/participantlists/%s/participants/%s";
    public static final String PATH_POST_PARTICIPANT_CREATE = "/participantlists/%s/participants";
    public static final String PATH_POST_PARTICIPANT_QUERY_CREATE = "/participantqueries/new";
    public static final String PATH_GET_PARTICIPANTS_USING_QUERY = "/participantlists/%s/participants?query=/participantqueries/%s";
    public static final String PATH_GET_ALL_PARTICIPANTS = "/participantlists/%s/participants";
    public static final String PATH_GET_PARTICIPANT = "/participantlists/%s/participants/%s";
    public static final String PATH_DELETE_PARTICIPANT = "/participantlists/%s/participants/%s";

    public static final String PATH_GET_SURVEYS = "/illumesurveys";
    public static final String PATH_POST_RESULT_QUERY_CREATE = "/resultqueries/new";
    public static final String PATH_GET_RESULTS_USING_QUERY = "/illumesurveys/%s/results?query=/resultqueries/%s";
    public static final String PATH_SURVEY_SESSION = "/illumesurveys/%s/surveysessions";

    public static final String GENERIC = "GENERIC";
    public static final String CURRENT_STATUS_UNKNOWN = "UNKNOWN";
    public static final String STATUS_DONE = "done";

    public enum NotificationStatus
    {
        NA, UNSENT, SENT
    }

    public static final String DATSTAT_ID = "datStatId";
    public static final String SURVEY_INFO = "surveyInfo";

    //these will only get set once
    private static String environment = null;
    private static String portalFrontendUrl = null;
    private static String portalFrontendGenericSurvey = null;
    private static String derivedParticipantListId = null;
    private static String masterParticipantListId = null;
    private static int additionalAttempts = 0;
    private static int sleepInMs = 500;
    private static boolean portalSurveyUI = true;
    private static String datStatUrl = null;
    private static String datStatParticipantFirstSurveyUrl = null;
    private static String datStatKey = null;
    private static String datStatSecret = null;
    private static String datStatUsername = null;
    private static String datStatPassword = null;
    private static String datStatParticipantStatusField = null;
    private static String datStatParticipantExitField = null;
    private static String datStatAddressValidField = null;
    private static String datStatAddressCheckField = null;
    private static Map<String, String> portalNotificationAttachmentLookup = new HashMap<>();
    private static JsonArray datStatParticipantWorkflowNotifications = null;
    private static Map<String, JsonElement> portalWorkflowNotificationLookup = new HashMap<>();
    private static Map<String, JsonElement> portalReminderNotificationLookup = new HashMap<>();
    private static Map<String, JsonElement> portalSurveyLookup = new HashMap<>();
    private static Map<String, String> portalNextSurveyLookup = new HashMap<>();
    private static JsonArray kitRequests = null;
    private static Map<String, JsonElement> currentNotificationLookup = new HashMap<>();
    private static Map<String, Map<String, Object>> datStatAnonSurveyNotifications = new HashMap<>();
    private static boolean datStatGenerateTestParticipants = false;
    private static boolean datStatHasParticipantLists = true;
    private static String emailClassName = null;
    private static String emailKey = null;
    private static JsonObject emailClientSettings = null;
    private static String invalidAddressRecipient = null;
    private static String invalidAddressSubject = null;
    private static boolean started = false;
    private static Map<String, SurveyConfig> surveyConfigMap;
    private static String institutionSurveyId = null;

    public enum MethodType
    {
        GET, POST, DELETE
    }

    private static enum InstitutionQuery
    {
        PARTICIPANT_ID(0), SUBMISSIONID(1), PHYSICIANS(2), INSTITUTIONS(3),
        BIOPSY_INSTITUTION(4),  BIOPSY_CITY(5), BIOPSY_STATE(6), LASTUPDATED(7),
        CREATED(8), FIRSTCOMPLETED(9), SHORTID(10), FIRSTNAME(11), LASTNAME(12);

        private final int idx;

        InstitutionQuery(int idx)  { this.idx = idx;}
        public int getIdx() { return idx;}
    }

    private static final String[] INSTITUTION_QUERY_VARIABLES = new String[] {ALTPID_FIELD, "DATSTAT.SUBMISSIONID",
            "PHYSICIAN_LIST", "INSTITUTION_LIST", "INITIAL_BIOPSY_INSTITUTION", "INITIAL_BIOPSY_CITY", "INITIAL_BIOPSY_STATE",
            "DDP_LASTUPDATED"};

    private static final String[] INSTITUTION_QUERY_MORE_VARIABLES = new String[] {"DDP_CREATED", "DDP_FIRSTCOMPLETED",
            "DDP_PARTICIPANT_SHORTID", "DATSTAT_FIRSTNAME", "DATSTAT_LASTNAME"};


    public DatStatUtil()
    {
        logger.info("Illume6_1 instance created.");
    }

    /**
     * Simplifies instantiation for testing.
     */
    public DatStatUtil(@NonNull Config config)
    {
        if (!config.getString("portal.environment").equals(Utility.Deployment.UNIT_TEST.toString()))
            throw new RuntimeException(LOG_PREFIX + "This constructor is only available for testing.");

        //reset static values
        reset();

        startup(config, null);
    }

    /**
     * Designed to run during app startup ONLY. It sets a bunch of static configuration variables for later use.
     * It was necessary to save those values so that the job scheduler could easily create new instances of this class using
     * the default constructor.
     *
     * @param config
     * @param scheduler might be null if we are testing
     */
    public synchronized void startup(@NonNull Config config, Scheduler scheduler)
    {
        if (started)
        {
            throw new RuntimeException(LOG_PREFIX + "Startup has already been called for this application.");
        }

        started = true;

        logger.info(LOG_PREFIX + "Setting up application level Illume6_1 instance.");
        logger.info(LOG_PREFIX + "Initializing AuthSingleton and setting static values...");

        environment = config.getString("portal.environment");
        portalFrontendUrl = config.getString("portal.frontendUrl");
        portalFrontendGenericSurvey = config.getString("portal.frontendGenericSurvey");

        additionalAttempts = config.getInt("datStat.additionalAttempts");
        sleepInMs = config.getInt("datStat.retryWaitMs");
        datStatUrl = config.getString("datStat.url");
        datStatKey = config.getString("datStat.key");
        datStatSecret = config.getString("datStat.secret");
        datStatUsername = config.getString("datStat.username");
        datStatPassword = config.getString("datStat.password");

        datStatHasParticipantLists = config.hasPath("datStat.hasParticipantLists") ? config.getBoolean("datStat.hasParticipantLists"): true;

        if (datStatHasParticipantLists) {
            datStatParticipantStatusField = config.getString("datStat.participantStatusField");
            datStatParticipantExitField = config.getString("datStat.participantExitField");
            datStatAddressCheckField = config.getString("datStat.addressCheckedField");
            datStatAddressValidField = config.getString("datStat.addressValidField");

            kitRequests = (JsonArray) (new JsonParser().parse(config.getString("kitRequests")));

            datStatGenerateTestParticipants = config.hasPath("datStat.generateTestParticipants") ? config.getBoolean("datStat.generateTestParticipants") : false;

            if (datStatGenerateTestParticipants) {
                logger.warn(LOG_PREFIX + "Only TEST participants will be created in this DatStat instance.");
            } else {
                logger.warn(LOG_PREFIX + "Non-test participants will be created in this DatStat instance.");
            }

            configureParticipantListIds(config.getString("datStat.derivedParticipantList"),
                    config.getString("datStat.masterParticipantList"));
        }

        emailClassName = config.getString("email.className");
        emailKey = config.getString("email.key");
        emailClientSettings = (JsonObject)(new JsonParser().parse(config.getString("email.clientSettings")));

        //if we should send out invalid email address notifications configure that here
        if (config.hasPath("email.invalidAddressAlertRecipientAddress")&&(StringUtils.isNotBlank(config.getString("email.invalidAddressAlertRecipientAddress")))) {
            invalidAddressRecipient = config.getString("email.invalidAddressAlertRecipientAddress");
            invalidAddressSubject = "Invalid Address Alert";
            if (!environment.equals(Utility.Deployment.PROD.toString())) {
                invalidAddressSubject += ": " + emailClientSettings.get("sendGridFromName").getAsString();
            }
        }

        configDatStatFrontend(config);


        createScheduledJobs(scheduler, config.getInt("portal.edcJobIntervalInSeconds"));
    }

    /**
     * Uses email to check to see if someone is already a participant in the derived participant list.
     * @param email unique address to check for (check is case insensitive)
     */
    public boolean participantExists(@NonNull String email)
    {
        logger.info(LOG_PREFIX + "Checking for participant...");

        boolean exists = false;

        try
        {
            if (getParticipantIdByEmail(email) != null) exists = true;
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to determine participant existence.", ex);
        }

        return exists;
    }

    /**
     * Post query definition JSON to DatStat.
     *
     * @param queryData
     * @return id to use to run the query later
     */
    public String addQuery(@NonNull Map<String, Object> queryData, @NonNull String additionalPath)
    {
        logger.info(LOG_PREFIX + "Adding query...");

        String queryId = null;
        try
        {
            HttpResponse response = sendRequest(MethodType.POST, additionalPath, queryData);

            JsonElement element = (JsonElement)(new JsonParser().parse(response.parseAsString()));

            Properties data = new Gson().fromJson(element, Properties.class);
            queryId = getLastIdFromUri(data.getProperty("Uri"));

            if (queryId == null)
            {
                throw new NullValueException("Id for query is null.");
            }

            logger.info(LOG_PREFIX + "Added new query.");
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new query.", ex);
        }
        return queryId;
    }

    /**
     * Fetches various participant attributes for the given participant
     * @param altPid public id sent by DSM
     * @param participantFields participant fields needed back
     * @return a participant json element with needed fields that are not null.
     * Field names are the DSM field names, so when looking for attributes
     * in the json, you'll want {@link ParticipantFields#getDSMValue()} and not {@link ParticipantFields#getDatStatValue()} ()}
     */
    public JsonElement getParticipantById(@NonNull String altPid, ParticipantFields[] participantFields) {
        logger.info(LOG_PREFIX + "finding a participant...");

        JsonElement participant = null;

        try
        {
            String participantId = getParticipantIdByAltPid(altPid);

            if (participantId != null)
            {
                HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANT, derivedParticipantListId, participantId), null);

                JsonElement element = new JsonParser().parse(response.parseAsString());
                JsonObject json = element.getAsJsonObject();
                JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

                Map<String, String> participantProperties = new HashMap<>();
                for (ParticipantFields p : participantFields)
                {
                    JsonElement field = participantJson.get(p.getDatStatValue());
                    if (!field.isJsonNull())
                    {
                        participantProperties.put(p.getDSMValue(), field.getAsString());
                    }
                }
                if (!participantProperties.isEmpty())
                {
                    String jsonPerson = new Gson().toJson(participantProperties); // Simone TODO check if GsonBuilder().serializeNulls() should be used
                    participant = new Gson().fromJson(jsonPerson, JsonElement.class);
                }
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to find a participant by altpid.", ex);
        }

        return participant;
    }

    /**
     *
     * @return JsonArray with (JsonObject) participantJson
     */
    @Override
    public JsonArray getAllParticipants(ParticipantFields[] participantFields) {
        JsonArray participantsArray = new JsonArray();
        try
        {
            HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_ALL_PARTICIPANTS, derivedParticipantListId), null);
            JsonArray entries = (JsonArray)(new JsonParser().parse(response.parseAsString()));
            logger.info("Found " + entries.size() + " participants");

            // iterate through all results array
            for (JsonElement entry : entries)
            {
                JsonElement participantUrl = entry.getAsJsonObject().get("Uri");
                HttpResponse participantResponse = sendRequest(MethodType.GET, participantUrl.getAsString(), null);
                String responseString = participantResponse.parseAsString();
                JsonElement participantJson = new JsonParser().parse(responseString);

                //for each participant select the properties from the datstat field list
                Map<String, String> participantProperties = new HashMap<>();
                for (ParticipantFields p : participantFields)
                {
                    JsonElement field = ((JsonObject) participantJson).get("ParticipantData").getAsJsonObject().get(p.getDatStatValue());
                    if (!field.isJsonNull())
                    {
                        participantProperties.put(p.getDSMValue(), field.getAsString());
                    }
                }
                if (!participantProperties.isEmpty())
                {
                    String jsonPerson = new Gson().toJson(participantProperties); // Simone TODO check if GsonBuilder().serializeNulls() should be used
                    JsonElement jsonElement = new Gson().fromJson(jsonPerson, JsonElement.class);
                    participantsArray.add(jsonElement);
                }
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to find all participants.", ex);
        }

        return participantsArray;
    }

    /**
     * Gets the DatStat id used to identify a particular participant.
     *
     * @param email used to query for the participant in the derived list
     */
    public String getParticipantIdByEmail(@NonNull String email)
    {
        logger.info(LOG_PREFIX + "Getting id for participant...");

        String participantId = null;

        try
        {
            String queryId = addParticipantExistenceQuery(email,"DATSTAT_EMAIL");

            HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);

            JsonArray entries = (JsonArray)(new JsonParser().parse(response.parseAsString()));

            if (entries.size() > 1)
            {
                //this should never happen because participant lists should be create such that the email property
                //is required and must be unique per list... but let's throw an exception here just in case.
                throw new DatStatRestApiException("Participant query found more than one participant with email: " + email);
            }

            for (JsonElement element : entries)
            {
                participantId = getLastIdFromUri(element.getAsJsonObject().get("Uri").getAsString());
            }

            logger.info(LOG_PREFIX + "Participant query ran successfully.");
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to query for a participant.", ex);
        }
        return participantId;
    }

    /**
     * Gets the DatStat id used to identify a particular participant.
     *
     * @param altPid used to query for the participant in the derived list
     */
    public String getParticipantIdByAltPid(@NonNull String altPid)
    {
        logger.info(LOG_PREFIX + "Getting id for participant...");

        String participantId = null;

        try
        {
            String queryId = addParticipantExistenceQuery(altPid, ALTPID_FIELD);

            HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);

            JsonArray entries = (JsonArray)(new JsonParser().parse(response.parseAsString()));

            if (entries.size() > 1)
            {
                //this should never happen because participant lists should be create such that the email property
                //is required and must be unique per list... but let's throw an exception here just in case.
                throw new DatStatRestApiException("Participant query found more than one participant with altPid: " + altPid);
            }

            for (JsonElement element : entries)
            {
                participantId = getLastIdFromUri(element.getAsJsonObject().get("Uri").getAsString());
            }

            logger.info(LOG_PREFIX + "Participant query ran successfully.");
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to query for a participant.", ex);
        }
        return participantId;
    }

    /**
     * 1) get RequestIds + ParticipantIds out of DatStat
     * 2) get UUId and RequestIds from DDP dB
     * 3) map them together , and for now return TYPE as constant datStatKitRequests.kitType
     *
     * @return an array of ParticipantIds, UUID(maps to RequestIds in DDPdb) and kitType
     */
    public JsonArray getKitRequestsDetails()  {
        logger.info(LOG_PREFIX + "getting all participants with requests");

        String queryId;
        HttpResponse response;
        String datStatRequestIdFieldName;
        JsonArray reqDetails = new JsonArray();

        try
        {
            //loop through all the kit types and get their request (* we are not saving kitType anywhere but might in the future)
            for (JsonElement requestInfo : kitRequests)
            {

                String kitType = requestInfo.getAsJsonObject().get("kitType").getAsString();
                datStatRequestIdFieldName = requestInfo.getAsJsonObject().get("id").getAsString();

                //if participant has a value in the request field - grab the participantId and that field value
                queryId = addParticipantRequestsQuery(datStatRequestIdFieldName, 0);

                response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);
                JsonArray datStatParticipantEntries = (JsonArray) (new JsonParser().parse(response.parseAsString()));

                logger.info("Found " + datStatParticipantEntries.size() + " participants with requestId > 0");

                Map<Integer, String> reqUUIDMap = new HashMap<>();
                if (datStatParticipantEntries.size() > 0)
                { //found participants with requestID
                    reqUUIDMap = Utility.getAllKitRequests();
                }
                reqDetails = populateKitDetailMap(datStatParticipantEntries, datStatRequestIdFieldName, kitType, reqUUIDMap);
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to get all kit requests.", ex);
        }
        return reqDetails;
    }

    /**
     *
     * @param datStatParticipantEntries array of participants
     * @param datStatRequestIdFieldName requestId fieldname
     * @param kitType hard coded for now
     * @param reqUUIDMap map of requestId and UUID from DB
     * @return a SORTED jsonArray of {participantId,kitRequestId,kitType } BY datStat requestId
     */
    private JsonArray populateKitDetailMap(JsonArray datStatParticipantEntries, String datStatRequestIdFieldName, String kitType,
                                           Map<Integer, String> reqUUIDMap)  {
        Gson gson = new Gson();

        JsonElement jsonElement = null;

        try
        {
            TreeSet<KitDetail> kitDetailsSorted = new TreeSet<>(new KitDetail.KitDetailComp());

            for (JsonElement entry : datStatParticipantEntries)
            {
                JsonElement participantUrl = entry.getAsJsonObject().get("Uri");
                HttpResponse participantResponse = sendRequest(MethodType.GET, participantUrl.getAsString(), null);
                String responseString = participantResponse.parseAsString();
                JsonElement participantJson = new JsonParser().parse(responseString); //datstat element

                //get participantId (ALTPID)
                JsonElement participantId = ((JsonObject) participantJson).get("ParticipantData").getAsJsonObject().get(ALTPID_FIELD);
                if (participantId.isJsonNull())
                {
                    continue;
                }
                JsonElement reqId = ((JsonObject) participantJson).get("ParticipantData").getAsJsonObject().get(datStatRequestIdFieldName);
                if (reqId.isJsonNull())
                {
                    continue;
                }

                int reqIdIntValue = Integer.parseInt(reqId.getAsString());
                String uuid = reqUUIDMap.get(reqIdIntValue);
                if (uuid == null)
                {
                    continue;
                }

                KitDetail kitDetails = new KitDetail(participantId.getAsString(), uuid, kitType, reqIdIntValue);
                kitDetailsSorted.add(kitDetails);
            }
            String kitDetailTreeSet = gson.toJson(kitDetailsSorted);
            jsonElement = new Gson().fromJson(kitDetailTreeSet, JsonElement.class);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred populating the kit detail map.", ex);
        }

        return (JsonArray)jsonElement;
    }

    /**
     * 1) get RequestIds + ParticipantIds out of DatStat
     * 2) get UUId and RequestIds from DDP dB
     * 3) map them together , and for now return TYPE as constant datStatKitRequests.kitType
     *
     * @return an aray of ParticipantIds, UUID(maps to RequestIds in DDPdb) and kitType
     */
    public JsonArray getKitRequestsDetails(@NonNull String UUID)  {
        logger.info(LOG_PREFIX + "getting all participants with requests after request id given ");

        String queryId;
        HttpResponse response;
        String datStatRequestIdFieldName;
        JsonArray reqDetails = new JsonArray();

        try
        {
            //loop through all the kit types and get their request (* we are not saving kitType anywhere but might in the future)
            for (JsonElement requestInfo : kitRequests)
            {

                String kitType = requestInfo.getAsJsonObject().get("kitType").getAsString();
                datStatRequestIdFieldName = requestInfo.getAsJsonObject().get("id").getAsString();

                //find the requestId mapped to the UUID
                int maxReqId = Utility.getKitRequestByUuid(UUID);

                //if participant has a value in the request field - grab the participantId and that field value
                queryId = addParticipantRequestsQuery(datStatRequestIdFieldName, maxReqId);

                response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);
                JsonArray datStatParticipantEntries = (JsonArray)(new JsonParser().parse(response.parseAsString()));

                logger.info("Found " + datStatParticipantEntries.size() + " participants with requerstId > " + maxReqId);

                Map<Integer, String> reqUUIDMap = new HashMap<>();
                if (datStatParticipantEntries.size() > 0)
                { //found participants with requestID
                    reqUUIDMap = Utility.getAllKitRequestsGTID(maxReqId); //gets only the latest records
                }
                reqDetails = populateKitDetailMap(datStatParticipantEntries, datStatRequestIdFieldName, kitType, reqUUIDMap);
            }
        }
        catch (DatStatKitRequestIdException kitEx) //we want the caller to be able to check for this
        {
            throw kitEx;
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to get kit requests.", ex);
        }

        return reqDetails;
    }

    /**
     * 1) Used by BG Process
     * 2) Determines which participant addresses we haven't tried to validate yet in DatStat
     * 3) Uses EasyPost to see if the address is valid
     * 4) Cleans up the address if it is valid, sets the checked field to 1, and sets the address valid field to 1/0 depending on whether the address
     * is valid
     *
     * @return total number of participant addresses we attempted to validate in DatStat
     */
    public int validateParticipantAddresses()
    {
        int totalAddresses = 0;

        if (EasyPost.apiKey.equals(SKIP_ADDRESS_VALIDATION) || !datStatHasParticipantLists)
        {
            logger.info(LOG_PREFIX + "Skip participant addresses validation.");
        }
        else
        {
            logger.info(LOG_PREFIX + "Searching for unvalidated participant addresses...");

            String queryId = null;
            HttpResponse responseList = null;
            HttpResponse participantResponse = null;
            JsonArray participantArray = null;
            String participantId = null;
            JsonObject participantJson = null;
            String checkField = null;
            Boolean success = true;

            try
            {
                EmailClient invalidAddressEmailClient = (EmailClient) Class.forName(emailClassName).newInstance();
                invalidAddressEmailClient.configure(emailKey, emailClientSettings, "", null, environment);

                queryId = addParticipantsNeedingAddressValidationQuery();

                logger.info(LOG_PREFIX + "Execute query for participant addresses requiring validation.");

                responseList = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);

                participantArray = (JsonArray) (new JsonParser().parse(responseList.parseAsString()));

                logger.info(LOG_PREFIX + "Total participant addresses requiring validation = " + participantArray.size() + ".");

                if (participantArray.size() > 0)
                {
                    //loop through the participants that require address validation
                    for (JsonElement listElement : participantArray)
                    {
                        String altPid = "";
                        try
                        {
                            participantId = getLastIdFromUri(listElement.getAsJsonObject().get("Uri").getAsString());

                            //retrieve all the info for the participant
                            participantResponse = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANT, derivedParticipantListId, participantId), null);

                            participantJson = (new JsonParser().parse(participantResponse.parseAsString())).getAsJsonObject().get("ParticipantData").getAsJsonObject();

                            if (participantJson == null)
                            {
                                throw new NullValueException("Response ParticipantData is null.");
                            }

                            altPid = participantJson.get(ALTPID_FIELD).getAsString();

                            String shortId = participantJson.get("DDP_PARTICIPANT_SHORTID").getAsString();
                            String status = participantJson.get(datStatParticipantStatusField).getAsString();

                            //let's validate the participant's address
                            DeliveryAddress deliveryAddress = new DeliveryAddress(
                                    getDatStatStringValue("DDP_STREET1", participantJson),
                                    getDatStatStringValue("DDP_STREET2", participantJson),
                                    getDatStatStringValue("DDP_CITY", participantJson),
                                    getDatStatStringValue("DDP_STATE", participantJson),
                                    getDatStatStringValue("DDP_POSTAL_CODE", participantJson),
                                    getDatStatStringValue("DDP_COUNTRY", participantJson));
                            deliveryAddress.validate();

                            //store the address
                            HashMap<String, Object> changes = new HashMap<>();
                            changes.put("DDP_STREET1", deliveryAddress.getStreet1());
                            changes.put("DDP_STREET2", deliveryAddress.getStreet2());
                            changes.put("DDP_CITY", deliveryAddress.getCity());
                            changes.put("DDP_STATE", deliveryAddress.getState());
                            changes.put("DDP_POSTAL_CODE", deliveryAddress.getZip());
                            changes.put("DDP_COUNTRY", deliveryAddress.getCountry());
                            changes.put(datStatAddressCheckField, 1);
                            changes.put(getDatStatAddressValidField(), (deliveryAddress.isValid() ? 1 : 0));

                            logger.info(LOG_PREFIX + "Updating address data for participant " + altPid + "...");

                            updateParticipantDataViaId(participantId, changes);

                            if ((status.equals(STATUS_DONE)&&(!deliveryAddress.isValid())&&(invalidAddressRecipient != null))) {
                                invalidAddressEmailClient.sendSingleNonTemplate(invalidAddressRecipient, invalidAddressSubject,
                                        String.format(INVALID_ADDRESS_MESSAGE, shortId), "INVALID_ADDRESS_ALERT");

                                logger.warn(LOG_PREFIX + "Invalid address notification sent for participant " + altPid + ".");
                            }

                            totalAddresses++;
                        }
                        catch (Exception ex)
                        {
                            success = false;
                            //for now swallow and log errors for individual participants so we can continue the loop
                            logger.error(LOG_PREFIX + "Unable to validate address for a participant " + altPid + ".", ex);
                        }
                    }
                }

                //if any of the above request id generations failed let's throw an exception now...
                if (!success)
                {
                    throw new DatStatAddressValidationException("An error occurred trying to validate a participant address.");
                }
            } catch (Exception ex)
            {
                logger.error(LOG_PREFIX + "An error occurred trying to validate participant addresses.", ex);
            }
        }

        return totalAddresses;
    }

    public static String getDatStatStringValue(@NonNull String fieldName, @NonNull JsonObject participantJson) {
        return participantJson.get(fieldName).isJsonNull() ? null : participantJson.get(fieldName).getAsString();
    }

    /**
     * 1) Used by BG Process
     * 2) Determines which follow-up surveys need to be created by checking a DB queue
     * 3) Updates queue records after creating surveys so they won't be created again
     *
     * @return sessionIds of all surveys created
     */
    public ArrayList<String> createFollowUpSurveys()
    {
        logger.info(LOG_PREFIX + "Checking for queued follow-up surveys...");

        ArrayList<String> sessionIds = new ArrayList();
        SurveyService service = new SurveyService();

        try
        {
            ArrayList<FollowUpSurveyRecord> records = FollowUpSurveyRecord.getRecordsForProcessing(datStatHasParticipantLists());

            for (FollowUpSurveyRecord surveyRecord : records) {
                try {
                    createFollowUpSurvey(sessionIds, surveyRecord, service);
                }
                catch (Exception ex) {
                    //for now swallow and log errors for individual participant surveys so we can continue the loop
                    logger.error(LOG_PREFIX + "Unable to process follow-up survey with record id =  " + surveyRecord.getRecordId() + ".", ex);
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(LOG_PREFIX + "An error occurred trying to create follow-up surveys.", ex);
        }

        return sessionIds;
    }

    public void createFollowUpSurvey(ArrayList<String> sessionIds, @NonNull FollowUpSurveyRecord surveyRecord, @NonNull SurveyService service) {
        SurveyConfig surveyConfig = getSurveyConfigMap().get(surveyRecord.getSurvey());

        Recipient recipient = null;

        if (datStatHasParticipantLists) {
            recipient = getSimpleParticipantInfoByAltPid(surveyRecord.getParticipantId());
        }
        else {
            recipient = surveyRecord.getRecipient();
        }

        if (recipient != null) {
            //check if survey was already created last time but maybe the db update failed
            String sessionId = getSingleFollowUpSurveySessionViaUri(surveyConfig.getSurveyDefinition().getUri(), surveyRecord.getParticipantId(),
                    surveyRecord.getFollowUpInstance());

            if (sessionId != null) {
                logger.warn(LOG_PREFIX + "Survey session " + sessionId + " already exists for record " + surveyRecord.getRecordId() + ".");
            }
            else {
                SurveyInstance surveyInstance = service.createSurveyInstance(this, surveyConfig.getSurveyDefinition(),
                        surveyConfig.getSurveyClass(), recipient, null, surveyRecord.getFollowUpInstance());
                sessionId = surveyInstance.getSurveySession().getSessionId();
            }

            if (sessionId == null) {
                throw new RuntimeException("Survey session id for new survey is blank.");
            }

            //this will only update one row at a time right now in case there is a problem with some surveys but not others
            FollowUpSurveyRecord.completeProcessing(surveyRecord.getRecordId());
            if (sessionIds != null) {
                sessionIds.add(sessionId);
            }
        } else {
            throw new RuntimeException("Recipient cannot be found.");
        }
    }

    /**
     * 1) Used by BG Process
     * 2) Determines which participants have missing kit request ids in DatStat
     * 3) Generates next request id and UUID
     * 4) Adds participant kit request ids to DatStat
     *
     * @return total number of kit request ids successfully populated in DatStat
     */
    public int populateKitRequestIds() {
        int totalPopulatedIds = 0;

        if (!datStatHasParticipantLists) {
            logger.info(LOG_PREFIX + "Skip request for missing kit request ids.");
        }
        else {
            logger.info(LOG_PREFIX + "Checking for missing kit request ids...");

            String queryId = null;
            HttpResponse response = null;
            JsonArray participantList = null;
            String participantId = null;
            String requestIdField = null;
            boolean success = true;

            try {
                //loop through all the kit request id types to see if any require an id to be generated
                for (JsonElement requestInfo : kitRequests) {
                    requestIdField = requestInfo.getAsJsonObject().get("id").getAsString();

                    logger.info(LOG_PREFIX + "Checking for missing kit request ids of type = " + requestIdField + "...");

                    JsonArray countries = null;

                    if (!(requestInfo.getAsJsonObject().get("countries") == null)) {
                        countries = requestInfo.getAsJsonObject().get("countries").getAsJsonArray();
                    }

                    boolean done = false;
                    if (requestInfo.getAsJsonObject().get("done") != null) {
                        done = requestInfo.getAsJsonObject().get("done").getAsBoolean();
                    }

                    queryId = addParticipantsNeedingKitRequestIdQuery(requestIdField,
                            requestInfo.getAsJsonObject().get("validAddressRequired").getAsBoolean(), countries, done);

                    logger.info(LOG_PREFIX + "Execute query for missing kit request ids of type " + requestIdField + ".");

                    response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);

                    participantList = (JsonArray) (new JsonParser().parse(response.parseAsString()));

                    logger.info(LOG_PREFIX + "Total missing kit request ids of type " + requestIdField + " = " + participantList.size() + ".");

                    if (participantList.size() > 0) {
                        //loop through the participants that require kit request ids
                        for (JsonElement listElement : participantList) {
                            participantId = getLastIdFromUri(listElement.getAsJsonObject().get("Uri").getAsString());

                            try {
                                //add kit request Id for this participant
                                addParticipantKitRequestId(requestIdField, participantId);
                                totalPopulatedIds++;
                            }
                            catch (Exception ex) {
                                success = false;
                                //for now swallow and log errors for individual participants so we can continue the loop
                                logger.error(LOG_PREFIX + "Unable to generate kit request id of type " + requestIdField + " for a participant.", ex);
                            }
                        }
                    }

                    //if any of the above request id generations failed let's throw an exception now...
                    if (!success) {
                        throw new DatStatKitRequestIdException(LOG_PREFIX + "An error occurred trying to add kit request id of type " + requestIdField + " for participants.");
                    }
                }
            }
            catch (Exception ex) {
                logger.error(LOG_PREFIX + "An error occurred trying to generate new kit request ids.", ex);
            }
        }

        return totalPopulatedIds;
    }

    /**
     * 1) Used by BG Process
     * 2) Determines which participant data is new
     * 3) Updates Db with latest info
     *
     * @return altPids of participants with updated/new data in Db
     */
    public ArrayList<String> syncParticipantDataInDb() {
        ArrayList<String> altPids = new ArrayList();

        if (!datStatHasParticipantLists) {
            logger.info(LOG_PREFIX + "Skip search for participant updates.");
        }
        else {

            logger.info(LOG_PREFIX + "Searching for participant updates...");

            String queryId = null;
            HttpResponse responseList = null;
            HttpResponse participantResponse = null;
            JsonArray participantArray = null;
            String participantId = null;
            JsonObject participantJson = null;

            try {
                String maxModifiedDate = Participant.getAdjustedMaxModified();

                queryId = addParticipantSyncQuery(maxModifiedDate);

                logger.info(LOG_PREFIX + "Execute query to find participant updates.");

                responseList = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANTS_USING_QUERY, derivedParticipantListId, queryId), null);

                participantArray = (JsonArray) (new JsonParser().parse(responseList.parseAsString()));

                logger.info(LOG_PREFIX + "Total participants with updated info = " + participantArray.size() + ".");

                if (participantArray.size() > 0) {
                    //loop through the participants that have updated info
                    for (JsonElement listElement : participantArray) {
                        String altPid = "";

                        try {
                            participantId = getLastIdFromUri(listElement.getAsJsonObject().get("Uri").getAsString());

                            //retrieve all the info for the participant
                            participantResponse = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANT, derivedParticipantListId, participantId), null);

                            participantJson = (new JsonParser().parse(participantResponse.parseAsString())).getAsJsonObject().get("ParticipantData").getAsJsonObject();

                            if (participantJson == null) {
                                throw new NullValueException("Response ParticipantData is null.");
                            }

                            altPid = participantJson.get(ALTPID_FIELD).getAsString();

                            if (Participant.syncParticipantData(participantJson)) {
                                altPids.add(altPid);
                            }
                        }
                        catch (Exception ex) {
                            //we are throwing an exception here -- NOT continuing to the next participant
                            throw new RuntimeException(LOG_PREFIX + "Unable to sync data for participant " + altPid + ".", ex);
                        }
                    }
                }
            }
            catch (Exception ex) {
                logger.error(LOG_PREFIX + "An error occurred trying to find participants with updated info.", ex);
            }
        }

        return altPids;
    }

    public String createAltpid(@NonNull String participantListName) {
        return participantListName.hashCode() + "." + java.util.UUID.randomUUID().toString();
    }

    /**
     * Posts a new participant to DatStat.
     */
    public String addParticipant(@NonNull BasicUser participant, @NonNull String participantListName)
    {
        logger.info(LOG_PREFIX + "Adding participant...");

        String participantAltPid = null;

        try
        {
            participantAltPid = createAltpid(participantListName);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put(ALTPID_FIELD, participantAltPid);
            parameters.put("DATSTAT_FIRSTNAME", participant.getFirstName());
            parameters.put("DATSTAT_LASTNAME", participant.getLastName());
            parameters.put("DATSTAT_EMAIL", participant.getEmail());
            parameters.put("DDP_PORTAL_URL", portalFrontendUrl);
            parameters.put("DDP_CREATED", Utility.getCurrentUTCDateTimeAsString());
            parameters.put("DDP_PARTICIPANT_SHORTID", Integer.toString(Utility.getNextShortParticipantId()));
            parameters.put(datStatAddressCheckField, 0);

            if (portalSurveyUI)
            {
                parameters.put(datStatParticipantStatusField, CURRENT_STATUS_UNKNOWN);
            }

            if (datStatGenerateTestParticipants) //make participant a test participant
            {
                parameters.put("DATSTAT_TEST", "1");
            }
            Map<String, Object> participantData = new HashMap<>();
            participantData.put("ParticipantData", parameters);

            HttpResponse response = sendRequest(MethodType.POST, String.format(PATH_POST_PARTICIPANT_CREATE, derivedParticipantListId), participantData);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonObject json = element.getAsJsonObject();
            JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

            if (participantJson == null)
            {
                throw new NullValueException(LOG_PREFIX + "Response ParticipantData is null.");
            }

            logger.info(LOG_PREFIX + "Added participant: " + participant.getEmail());
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred while adding the participant.", ex);
        }

        return participantAltPid;
    }

    /**
     * Queues a single email for a participant. Email will differ depending upon the current status of the participant.
     */
    public void sendEmailToParticipant(@NonNull String email)
    {
        logger.info(LOG_PREFIX + "Queuing email for participant...");

        try
        {
            String participantId = getParticipantIdByEmail(email);
            Recipient recipient = null;
            JsonElement notificationInfo = null;

            if (participantId != null)
            {
                recipient = createRecipientUsingDatStat(participantId);

                //find the correct notification info for this participant
                notificationInfo = currentNotificationLookup.get(recipient.getCurrentStatus());

                if (notificationInfo == null)
                {
                    throw new DatStatRestApiException("Unable to determine notification information for participant with status = " +
                            recipient.getCurrentStatus() + " and email = " + email + ".");
                }

                if (!portalSurveyUI)
                {
                    queueEmail(recipient.getId(), recipient,null, notificationInfo, null);
                }
                else
                {
                    String sessionId = null;

                    //don't try to grab session id for done status since there won't be any
                    if (!recipient.getCurrentStatus().equals(STATUS_DONE))
                    {
                        SurveyConfig config = surveyConfigMap.get(recipient.getCurrentStatus());
                        sessionId = getSingleSurveySessionViaUri(config.getSurveyDefinition().getUri(), recipient.getId());
                        if (sessionId == null)
                        {
                            throw new DatStatRestApiException("Unable to find current survey session for participant with status = " +
                                    recipient.getCurrentStatus() + " and email = " + recipient.getEmail() + ".");
                        }
                    }
                    else
                    {
                        //we will fill the participant's survey link map with links to surveys they completed...
                        for (SurveyConfig surveyConfig : surveyConfigMap.values())
                        {
                            if (!surveyConfig.isAnonymousSurvey()&&(surveyConfig.getFollowUpType() == SurveyConfig.FollowUpType.NONE))
                            {
                                addSurveyLinkToRecipient(surveyConfig.getSurveyPathName(), recipient);
                            }
                        }
                    }
                    queueEmail(sessionId, recipient, null, notificationInfo, recipient.getCurrentStatus());
                }
            }
            else
            {
                throw new DatStatRestApiException("Unable to find participant with email " + email + ".");
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to queue email for participant with email " + email + ".", ex);
        }
    }

    /**
     * Gets called by survey processors to queue "sendnow" and reminder emails. For now it will swallow exceptions and just log them.
     */
    public void queueCurrentAndFutureEmailsForLocalSurveyUI(String sessionId, @NonNull String currentSurvey, @NonNull Recipient recipient,
                                                            String completedSurvey)
    {
        String email = null;
        try
        {
            email = recipient.getEmail();

            JsonElement notificationInfo = portalWorkflowNotificationLookup.get(currentSurvey);

            if (notificationInfo != null) {
                queueEmail(sessionId, recipient, getPortalReminderNotifications(currentSurvey), notificationInfo, currentSurvey);
            }
            else {
                logger.warn(LOG_PREFIX + "Unable to find email template for user with email = " + email + " and currentSurvey = " + currentSurvey + ".");
            }

            if (completedSurvey != null) EmailRecord.removeOldReminders(email, completedSurvey, true);
        }
        catch (Exception ex)
        {
            //NOTE: we need to swallow this because frontend should NOT get a 500 for this
            logger.error(LOG_PREFIX + "Unable to queue email(s) for user with email = " + email + " and status = " + currentSurvey + ".", ex);
        }
    }

    /**
     * Generates and stores the link to an authenticated survey for a participant. Only used for emails that require multiple links.
     * @param surveyName
     * @param recipient
     */
    private void addSurveyLinkToRecipient(@NonNull String surveyName, @NonNull Recipient recipient)
    {
        SurveyConfig config = surveyConfigMap.get(surveyName);

        String sessionId = getSingleSurveySessionViaUri(config.getSurveyDefinition().getUri(), recipient.getId());
        if (sessionId == null)
        {
            throw new DatStatRestApiException("Unable to find survey session for participant for survey = " +
                    surveyName + " with email = " + recipient.getEmail() + ".");
        }

        //find the correct notification info for this survey (used for URL)
        JsonElement notificationInfo = currentNotificationLookup.get(surveyName);

        if (notificationInfo == null)
        {
            throw new DatStatRestApiException("Unable to find notification info for participant for survey = " +
                    surveyName + " with email = " + recipient.getEmail() + ".");
        }

        String url = notificationInfo.getAsJsonObject().get("url").getAsString();

        String generatedUrl = generateSurveyUrlForLocalUI(url, surveyName, sessionId);
        recipient.getSurveyLinks().put(":" + surveyName + "Url", generatedUrl);
    }

    /**
     * NOTE: idForUrl is general id because it can be sessionId or altPid, basically some sort of id that is used to generate survey URL in email
     */
    private void queueEmail(String idForUrl, @NonNull Recipient recipient, JsonElement reminderInfo, @NonNull JsonElement notificationInfo, String currentSurvey)
    {
        try
        {
            JsonObject notificationObject = notificationInfo.getAsJsonObject();

            String url = notificationObject.get("url").getAsString();
            String pdfUrl = (notificationObject.get("pdfUrl") != null) ? notificationObject.get("pdfUrl").getAsString() : "";

            if (!portalSurveyUI)
            {
                recipient.setUrl(generateParticipantSurveyUrlForDatStatUI(url, idForUrl));
            }
            else
            {
                recipient.setUrl(generateSurveyUrlForLocalUI(url, currentSurvey, idForUrl));
                recipient.setPdfUrl(generatePdfUrlForLocalUI(pdfUrl, recipient.getId().replace(".", "!"))); //handle angular bug!
            }

            EmailRecord.add(notificationInfo, currentSurvey, recipient, reminderInfo, recipient.getEmail());
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to queue email for " + recipient.getEmail() + ".", ex);
        }
    }

    /**
     * Used to send requests of any type to DatStat with retries in case the token is bad.
     */
    public HttpResponse sendRequest(@NonNull MethodType method, @NonNull String additionalPath, Object postData)
    {
        logger.info(LOG_PREFIX + "Sending request...");

        HttpContent content = null;

        if (postData != null) content = new JsonHttpContent(new JacksonFactory(), postData);

        HttpResponse response = null;

        //we will always try again if the first attempt fails because the token may be invalid
        int totalAttempts = 2 + additionalAttempts;
        Exception ex = null;

        try
        {
            for (int i = 1; i <= totalAttempts; i++)
            {
                //if this isn't the first attempt let's wait a little before retrying...
                if (i > 1)
                {
                    logger.info(LOG_PREFIX + "Sleeping before request retry for " + sleepInMs + " ms.");
                    Thread.sleep(sleepInMs);
                    logger.info(LOG_PREFIX + "Sleeping done.");
                }

                try
                {
                    //after the first attempt we will keep resetting the token stuff in case that helps...
                    response = sendSingleRequest((i > 1), method, additionalPath, content);
                    break;
                }
                catch (Exception newEx)
                {
                    logger.warn(LOG_PREFIX + "Send request failed (attempt #" + i + " of " + totalAttempts + "): ", newEx);
                    ex = newEx;
                }
            }
        }
        catch (Exception outerEx)
        {
            throw new DatStatRestApiException("Unable to send requests.", ex);
        }

        if (response == null)
        {
            throw new DatStatRestApiException("Unable to send requests after retries.", ex);
        }

        return response;
    }

    /**
     * Creates a string of json containing an array of ParticipantSurveyInfo objects for a single follow-up survey.
     */
    public String getParticipantFollowUpInfo(@NonNull String survey) {
        logger.info(LOG_PREFIX + "Getting participant follow-up info for " + survey + "...");

        Map<String, ParticipantSurveyInfo> followUpSurveys = FollowUpSurveyRecord.getParticipantSurveyInfo(survey);

        if (!followUpSurveys.isEmpty()) {
            try
            {
                String queryId = addSurveyInfoQuery();
                String surveyId = getLastIdFromUri(getSurveyConfigMap().get(survey).getSurveyDefinition().getUri());

                logger.info(LOG_PREFIX + "Execute survey info query...");
                HttpResponse response = sendRequest(DatStatUtil.MethodType.GET, String.format(PATH_GET_RESULTS_USING_QUERY, surveyId, queryId), null);

                JsonElement element = new JsonParser().parse(response.parseAsString());
                JsonArray resultList = element.getAsJsonObject().get("Data").getAsJsonArray();

                logger.info(LOG_PREFIX + "Total surveys found = " + resultList.size() + ".");

                if (resultList.size() > 0)
                {
                    for (JsonElement resultElement : resultList)
                    {
                        JsonArray resultItems = resultElement.getAsJsonArray();

                        String altPid = resultItems.get(0).getAsString();
                        String followUpInstance = resultItems.get(1).getAsString();
                        String surveyStatus = AbstractSurveyInstance.getSubmissionStatus(resultItems.get(2).getAsInt()).toString();
                        String shortId = resultItems.get(3).getAsString();
                        String key = FollowUpSurveyRecord.generateAltPidInstanceKey(altPid, followUpInstance);

                        if (followUpSurveys.containsKey(key)) {
                            ParticipantSurveyInfo record = followUpSurveys.get(key);
                            record.setSurveyStatus(surveyStatus);
                            record.setShortId(shortId);
                        }
                        else {
                            logger.warn("Skipping key = " + key + " (not in follow-up queue anymore).");
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                throw new DatStatRestApiException("An error occurred trying to retrieve participant follow-up survey info.", ex);
            }
        }

        return new Gson().toJson(followUpSurveys.values());
    }

    /**
     * Creates a string of json containing an array of InstitutionRequest objects.
     *
     * @param lastId last SubmissionId processed by requestor (use 0 to return all)
     * @return
     */
    public String getInstitutionRequests(@NonNull int lastId)
    {
        logger.info(LOG_PREFIX + "Getting institution requests...");

        String institutionRequests = null;

        TreeSet<InstitutionRequest> requestsSorted = new TreeSet<>(new InstitutionRequest.InstitutionsComp());
        InstitutionRequest request = null;
        ArrayList<Institution> institutionList = null;

        try
        {
            String queryId = addSurveyInstitutionQuery(lastId, INSTITUTION_QUERY_VARIABLES, true);

            logger.info(LOG_PREFIX + "Execute survey institution query...");
            HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_RESULTS_USING_QUERY, institutionSurveyId, queryId), null);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonArray resultList = element.getAsJsonObject().get("Data").getAsJsonArray();

            logger.info(LOG_PREFIX + "Total surveys found = " + resultList.size() + ".");

            if (resultList.size() > 0)
            {
                for (JsonElement resultElement : resultList)
                {
                    JsonArray resultItems = resultElement.getAsJsonArray();

                    request = new InstitutionRequest();
                    request.setParticipantId(resultItems.get(InstitutionQuery.PARTICIPANT_ID.getIdx()).getAsString());
                    request.setId(resultItems.get(InstitutionQuery.SUBMISSIONID.getIdx()).getAsInt());
                    request.setLastUpdated(resultItems.get(InstitutionQuery.LASTUPDATED.getIdx()).getAsString().replace("UTC", "Z"));

                    institutionList = new ArrayList<>();

                    if (!resultItems.get(InstitutionQuery.PHYSICIANS.getIdx()).isJsonNull())
                    {
                        PhysicianInfo[] physicianInfoList = new Gson().fromJson(resultItems.get(InstitutionQuery.PHYSICIANS.getIdx()).getAsString(), PhysicianInfo[].class);
                        for (PhysicianInfo physician : physicianInfoList)
                        {
                            if (physician.getPhysicianId() != null)
                                institutionList.add(new Institution(physician.getPhysicianId(), Institution.InstitutionType.PHYSICIAN.toString()));
                        }
                    }

                    if (!resultItems.get(InstitutionQuery.INSTITUTIONS.getIdx()).isJsonNull())
                    {
                        InstitutionInfo[] institutionInfoList = new Gson().fromJson(resultItems.get(InstitutionQuery.INSTITUTIONS.getIdx()).getAsString(), InstitutionInfo[].class);
                        for (InstitutionInfo institution : institutionInfoList)
                        {
                            if (institution.getInstitutionId() != null)
                                institutionList.add(new Institution(institution.getInstitutionId(), Institution.InstitutionType.INSTITUTION.toString()));
                        }
                    }

                    if ((!resultItems.get(InstitutionQuery.BIOPSY_INSTITUTION.getIdx()).isJsonNull())||
                            (!resultItems.get(InstitutionQuery.BIOPSY_CITY.getIdx()).isJsonNull())||
                            (!resultItems.get(InstitutionQuery.BIOPSY_STATE.getIdx()).isJsonNull()))
                    {
                        institutionList.add(new Institution(Institution.INITIAL_BIOPSY_ID, Institution.InstitutionType.INITIAL_BIOPSY.toString()));
                    }

                    if (institutionList.size() > 0)
                    {
                        request.setInstitutions(institutionList);
                        requestsSorted.add(request);
                    }
                }
            }

            institutionRequests = new Gson().toJson(requestsSorted);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to retrieve institution requests.", ex);
        }

        return institutionRequests;
    }

    /**
     * Creates a string of json containing an array of ParticipantInstitutionInfo objects.
     */
    public String getParticipantInstitutionInfo()
    {
        logger.info(LOG_PREFIX + "Getting participant institution info...");

        ArrayList<ParticipantInstitutionInfo> participantInstitutionList = new ArrayList<>();
        ParticipantInstitutionInfo participantInstitutionInfo = null;
        ArrayList<InstitutionDetail> institutionList = null;

        Map<String, Participant> participants = Participant.getParticipantsFromDbWithStatus(STATUS_DONE);

        try
        {
            String queryId = addSurveyInstitutionQuery(0, ArrayUtils.addAll(INSTITUTION_QUERY_VARIABLES, INSTITUTION_QUERY_MORE_VARIABLES), false);

            logger.info(LOG_PREFIX + "Execute survey institution query...");
            HttpResponse response = sendRequest(DatStatUtil.MethodType.GET, String.format(PATH_GET_RESULTS_USING_QUERY, institutionSurveyId, queryId), null);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonArray resultList = element.getAsJsonObject().get("Data").getAsJsonArray();

            logger.info(LOG_PREFIX + "Total surveys found = " + resultList.size() + ".");

            if (resultList.size() > 0)
            {
                for (JsonElement resultElement : resultList)
                {
                    JsonArray resultItems = resultElement.getAsJsonArray();

                    String altPid = resultItems.get(InstitutionQuery.PARTICIPANT_ID.getIdx()).getAsString();
                    if (participants.containsKey(altPid)) {
                        Participant participant = participants.get(altPid);
                        participantInstitutionInfo = new ParticipantInstitutionInfo();
                        participantInstitutionInfo.setParticipantId(altPid);
                        participantInstitutionInfo.setSurveyLastUpdated(resultItems.get(InstitutionQuery.LASTUPDATED.getIdx()).getAsString().replace("UTC", "Z"));
                        participantInstitutionInfo.setSurveyCreated(resultItems.get(InstitutionQuery.CREATED.getIdx()).getAsString().replace("UTC", "Z"));
                        participantInstitutionInfo.setSurveyFirstCompleted(resultItems.get(InstitutionQuery.FIRSTCOMPLETED.getIdx()).getAsString().replace("UTC", "Z"));

                        participantInstitutionInfo.setShortId(participant.getShortId());
                        participantInstitutionInfo.setFirstName(participant.getFirstName());
                        participantInstitutionInfo.setLastName(participant.getLastName());
                        participantInstitutionInfo.setAddressValid(participant.getAddressValid());
                        participantInstitutionInfo.setAddress(participant.getAddress());

                        institutionList = InstitutionDetail.getCombinedInstitutions(resultItems.get(InstitutionQuery.PHYSICIANS.getIdx()),
                                resultItems.get(InstitutionQuery.INSTITUTIONS.getIdx()),
                                resultItems.get(InstitutionQuery.BIOPSY_INSTITUTION.getIdx()),
                                resultItems.get(InstitutionQuery.BIOPSY_CITY.getIdx()),
                                resultItems.get(InstitutionQuery.BIOPSY_STATE.getIdx()));

                        if (institutionList.size() > 0) {
                            participantInstitutionInfo.setInstitutions(institutionList);
                            participantInstitutionList.add(participantInstitutionInfo);
                        }
                    }
                    else {
                        logger.warn("Skipping participant with altpid = " + altPid + " (not in participant table with done status).");
                    }
                }
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to retrieve participant institution info.", ex);
        }

        return new Gson().toJson(participantInstitutionList);
    }

    /**
     * Creates a query to get participant Ids, institution info, etc. from a survey (typically release survey).
     *
     * NOTE: Every time a survey session is saved (PATCH OR PUT) the DATSTAT.SUBMISSIONID increases for it and
     * DATSTAT.SUBMISSIONID is unique for a given survey. So using a "> lastId" filter allows us to query for
     * all the survey sessions that have been touched since lastSubmissionId. This is handy because surveys
     * can be "always editable" so there may be changes to institutions of "completed surveys" over time. This also
     * means that consumers of the results of this query need to realized they may get repeat data.
     * @param lastId last SubmissionId processed by requestor (use 0 to return all)
     * @param variables survey fields to return
     * @param completedOnly filter for data from completed surveys only (no partial or terminated)
     * @return
     */
    private String addSurveyInstitutionQuery(@NonNull int lastId, String[] variables, boolean completedOnly)
    {
        logger.info(LOG_PREFIX + "Adding survey institution query...");

        String queryId = null;
        try
        {
            //first create a result query
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("IncludeRawData", true);
            queryData.put("IncludeSummaryData", false);
            queryData.put("IncludePartialData", true); //this must be true to include non-completed surveys
            queryData.put("TestDataOnly", false);
            queryData.put("TestSurvey", false);
            queryData.put("Variables", variables);
            queryData.put("CrossTabVariable", null);
            queryData.put("Sort", null);

            Map<String, Object> filters = new HashMap<>();

            String complexExpression = "FIRST and AGREEMENT and SUBMISSION and (PHYSICIAN or INSTITUTION or BIOPSY_INSTITUTION or BIOPSY_CITY or BIOPSY_STATE)";

            //completed surveys only
            if (completedOnly) {
                complexExpression = "COMPLETION and " + complexExpression;
                Map<String, Object> completionFilter = new HashMap<>();
                completionFilter.put("Variable", "DATSTAT.SUBMISSIONSTATUS");
                completionFilter.put("QueryOperator", "EqualTo");
                completionFilter.put("Value", SurveyInstance.SubmissionStatus.COMPLETE.getDatstatValue());
                filters.put("COMPLETION", completionFilter);
            }

            //let's filter so we only get surveys that were completed at some point (no partial surveys)
            Map<String, Object> firstCompletedFilter = new HashMap<>();
            firstCompletedFilter.put("Variable", "DDP_FIRSTCOMPLETED");
            firstCompletedFilter.put("QueryOperator", "IsNotNull");
            filters.put("FIRST", firstCompletedFilter);

            //agreement checkbox must be yes (this basically filters out completed surveys that were completed via unit tests)
            Map<String, Object> agreementFilter = new HashMap<>();
            agreementFilter.put("Variable", "AGREEMENT.AGREE");
            agreementFilter.put("QueryOperator", "EqualTo");
            agreementFilter.put("Value", 1);
            filters.put("AGREEMENT", agreementFilter);

            //query for all the survey sessions that have been touched since this lastId
            Map<String, Object> submissionFilter = new HashMap<>();
            submissionFilter.put("Variable", "DATSTAT.SUBMISSIONID");
            submissionFilter.put("QueryOperator", "GreaterThan");
            submissionFilter.put("Value", lastId);
            filters.put("SUBMISSION", submissionFilter);

            //make sure the physician list isn't [] -- seems to be special so [] is inside []
            Map<String, Object> physicianFilter = new HashMap<>();
            physicianFilter.put("Variable", "PHYSICIAN_LIST");
            physicianFilter.put("QueryOperator", "NotEqualTo");
            physicianFilter.put("Value", "[[]]");
            filters.put("PHYSICIAN", physicianFilter);

            //make sure the institution list isn't []
            Map<String, Object> institutionFilter = new HashMap<>();
            institutionFilter.put("Variable", "INSTITUTION_LIST");
            institutionFilter.put("QueryOperator", "NotEqualTo");
            institutionFilter.put("Value", "[[]]");
            filters.put("INSTITUTION", institutionFilter);

            //make sure the biopsy institution exists
            Map<String, Object> biopsyInstitutionFilter = new HashMap<>();
            biopsyInstitutionFilter.put("Variable", "INITIAL_BIOPSY_INSTITUTION");
            biopsyInstitutionFilter.put("QueryOperator", "NotEqualTo");
            biopsyInstitutionFilter.put("Value", "");
            filters.put("BIOPSY_INSTITUTION", biopsyInstitutionFilter);

            //make sure the biopsy city exists
            Map<String, Object> biopsyCityFilter = new HashMap<>();
            biopsyCityFilter.put("Variable", "INITIAL_BIOPSY_CITY");
            biopsyCityFilter.put("QueryOperator", "NotEqualTo");
            biopsyCityFilter.put("Value", "");
            filters.put("BIOPSY_CITY", biopsyCityFilter);

            //make sure the biopsy state exists
            Map<String, Object> biopsyStateFilter = new HashMap<>();
            biopsyStateFilter.put("Variable", "INITIAL_BIOPSY_STATE");
            biopsyStateFilter.put("QueryOperator", "NotEqualTo");
            biopsyStateFilter.put("Value", "");
            filters.put("BIOPSY_STATE", biopsyStateFilter);

            queryData.put("Filters", filters);
            queryData.put("QueryFilterType", 2);
            queryData.put("ComplexExpression", complexExpression);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_RESULT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new institution survey query.", ex);
        }
        return queryId;
    }

    public String generateParticipantFirstSurveyUrl(@NonNull String participantAltPid)
    {
        return generateParticipantSurveyUrlForDatStatUI(datStatParticipantFirstSurveyUrl, participantAltPid);
    }

    public String generateParticipantSurveyUrlForDatStatUI(@NonNull String path, @NonNull String participantAltPid)
    {
        if (!path.isEmpty())
        {
            if (!path.equals(GENERIC)) return datStatUrl + String.format(path, participantAltPid);
            else return portalFrontendUrl + String.format(portalFrontendGenericSurvey, participantAltPid);
        }
        return "";
    }

    public String generateSurveyUrlForLocalUI(@NonNull String path, @NonNull String surveyPath, String sessionId)
    {
        if (!path.isEmpty())
        {
            return portalFrontendUrl + String.format(path, surveyPath, sessionId);
        }
        return "";
    }

    public String generatePdfUrlForLocalUI(String path, String id)
    {
        if (StringUtils.isNotBlank(path))
        {
            return portalFrontendUrl + String.format(path, id);
        }
        return "";
    }

    public String generateAnonSurveyUrl(@NonNull String surveyName)
    {
        JsonElement surveyInfo = (JsonElement)datStatAnonSurveyNotifications.get(surveyName).get(SURVEY_INFO);
        return datStatUrl + surveyInfo.getAsJsonObject().get("url").getAsString();
    }

    public boolean datStatHasParticipantLists() {
        return datStatHasParticipantLists;
    }
    public String getDerivedParticipantListId()
    {
        return derivedParticipantListId;
    }

    public JsonArray getDatStatParticipantWorkflowNotifications()
    {
        return datStatParticipantWorkflowNotifications;
    }

    public Map<String, JsonElement> getCurrentNotificationLookup()
    {
        return currentNotificationLookup;
    }

    public Map<String, Map<String, Object>> getAnonSurveyNotifications()
    {
        return datStatAnonSurveyNotifications;
    }

    public String getMasterParticipantListId()
    {
        return masterParticipantListId;
    }

    public String getSurveyIdForAnonSurveyNotifications(String surveyName)
    {
        return datStatAnonSurveyNotifications.get(surveyName).get(DATSTAT_ID).toString();
    }

    public JsonElement getPortalSurveyInfo(String surveyPath)
    {
        return portalSurveyLookup.get(surveyPath);
    }

    public Map<String, JsonElement> getPortalSurveyLookup()
    {
        return portalSurveyLookup;
    }

    public String getPortalNextSurvey(String surveyPath)
    {
        return portalNextSurveyLookup.get(surveyPath);
    }

    public String getPortalNotificationAttachmentClassNames(String templateName)
    {
        return portalNotificationAttachmentLookup.get(templateName);
    }

    public JsonElement getPortalNotification(String surveyName)
    {
        return portalWorkflowNotificationLookup.get(surveyName);
    }

    public JsonElement getPortalReminderNotifications(String surveyName)
    {
        return portalReminderNotificationLookup.get(surveyName);
    }

    private String generateSurveyUrl(@NonNull String path)
    {
        return datStatUrl + path;
    }

    /**
     * Configures DatStat notification settings if using DatStat for survey UI.
     * @param config
     */
    private void configDatStatFrontend(Config config)
    {
        portalSurveyUI = Boolean.parseBoolean(config.getString("portal.portalSurveyUI"));

        if (portalSurveyUI)
        {
            logger.info(LOG_PREFIX + "Using portal UI for surveys.");

            JsonArray array = (JsonArray)(new JsonParser().parse(config.getString("portal.notifications")));
            for (JsonElement notificationInfo : array)
            {
                portalWorkflowNotificationLookup.put(notificationInfo.getAsJsonObject().get("id").getAsString(), notificationInfo);
            }

            array = (JsonArray)(new JsonParser().parse(config.getString("portal.surveys")));
            for (JsonElement surveyInfo : array)
            {
                portalSurveyLookup.put(surveyInfo.getAsJsonObject().get("surveyPath").getAsString(), surveyInfo);
                portalNextSurveyLookup.put(surveyInfo.getAsJsonObject().get("surveyPath").getAsString(), surveyInfo.getAsJsonObject().get("nextSurvey").getAsString());
            }

            array = (JsonArray)(new JsonParser().parse(config.getString("portal.notificationAttachments")));
            for (JsonElement attachmentInfo : array)
            {
                portalNotificationAttachmentLookup.put(attachmentInfo.getAsJsonObject().get("sendGridTemplate").getAsString(), attachmentInfo.getAsJsonObject().get("customAttachmentClassNames").getAsString());
            }

            array = (JsonArray)(new JsonParser().parse(config.getString("portal.reminderNotifications")));
            for (JsonElement reminderInfo : array)
            {
                portalReminderNotificationLookup.put(reminderInfo.getAsJsonObject().get("id").getAsString(), reminderInfo.getAsJsonObject().get("reminders").getAsJsonArray());
            }

            try
            {
                surveyConfigMap = SurveyConfig.buildSurveyConfig(this, portalSurveyLookup);
            }
            catch (Exception ex)
            {
                throw new DatStatRestApiException("Unable to setup survey config map.", ex);
            }
        }
        else
        {
            logger.info(LOG_PREFIX + "Using DatStat UI for surveys.");
            datStatParticipantFirstSurveyUrl = config.getString("datStat.participantFirstSurveyUrl");
            datStatParticipantWorkflowNotifications = (JsonArray)(new JsonParser().parse(config.getString("datStat.participantWorkflowNotifications")));
            configureAnonSurveyNotifications(config.getString("datStat.anonymousSurveyNotifications"));
        }

        JsonArray notifications = (JsonArray)(new JsonParser().parse(config.getString("participantStatusNotifications")));
        for (JsonElement notificationInfo : notifications)
        {
            currentNotificationLookup.put(notificationInfo.getAsJsonObject().get("id").getAsString(), notificationInfo);
        }
    }

    /**
     * Creates a query for anonymous survey results that can then be used to query a particular survey.
     *
     * @param surveyBookmark highest submission id from the last time the query was used successfully for a particular survey...
     */
    private String addCompletedAnonSurveyNeedingNotificationQuery(int surveyBookmark)
    {
        logger.info(LOG_PREFIX + "Adding completed anonymous survey notification query...");

        String queryId = null;
        try
        {
            //first create a result query
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("IncludeRawData", true);
            queryData.put("IncludeSummaryData", false);
            queryData.put("IncludePartialData", false);
            queryData.put("TestDataOnly", false);
            queryData.put("TestSurvey", false);
            queryData.put("Variables", new String[] {"DDP_EMAIL", "DATSTAT.SUBMISSIONID"});
            queryData.put("CrossTabVariable", null);
            queryData.put("Sort", null);

            Map<String, Object> submissionFilter = new HashMap<>();
            submissionFilter.put("Variable", "DATSTAT.SUBMISSIONID");
            submissionFilter.put("QueryOperator", "GreaterThan");
            submissionFilter.put("Value", surveyBookmark);
            Map<String, Object> filter = new HashMap<>();
            filter.put("SUBMISSIONID_FILTER", submissionFilter);
            queryData.put("Filters", filter);

            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_RESULT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new completed anonymous survey notification query.", ex);
        }
        return queryId;
    }

    /**
     * Creates a query for "UNSENT" notifications of a particular type.
     *
     * @param notificationField name of the notification field in DatStat
     */
    private String addParticipantsNeedingNotificationQuery(@NonNull String notificationField)
    {
        logger.info(LOG_PREFIX + "Adding participant notification query...");

        String queryId = null;
        try
        {
            Map<String, Object> notificationFilter = new HashMap<>();
            notificationFilter.put("Variable", notificationField);
            notificationFilter.put("QueryOperator", "EqualTo");
            notificationFilter.put("Value", NotificationStatus.UNSENT.toString());
            Map<String, Object> filter = new HashMap<>();
            filter.put("NOTIFICATION_STATUS", notificationFilter);
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", null);
            queryData.put("Filters", filter);
            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new participant notification query.", ex);
        }
        return queryId;
    }

    /**
     *
     * @param requestIdField requestId field name to look for value > 0
     * @return queryId
     */
    private String addParticipantRequestsQuery(@NonNull String requestIdField, int value) {
        logger.info(LOG_PREFIX + "finding participants with requests...");

        String queryId = null;
        try
        {
            Map<String, Object> kitFilter = new HashMap<>();
            kitFilter.put("Variable", requestIdField);
            kitFilter.put("QueryOperator", "GreaterThan");
            kitFilter.put("Value", value); //when this is zero a request Id has not been generated yet , so looking for gt 0
            Map<String, Object> filters = new HashMap<>();
            filters.put("KIT_FILTER", kitFilter);

            addExitedFilter(filters, null);

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", null);
            queryData.put("Filters", filters);
            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("couldn't execute the query with request field ."+requestIdField, ex);
        }
        return queryId;
    }

    /**
     * Creates a query for participants that need their addresses validated.
     */
    private String addParticipantsNeedingAddressValidationQuery()
    {
        logger.info(LOG_PREFIX + "Adding address validation query...");

        String queryId = null;
        try
        {
            Map<String, Object> filters = new HashMap<>();

            Map<String, Object> addressFilter = new HashMap<>();
            addressFilter.put("Variable", datStatAddressCheckField);
            addressFilter.put("QueryOperator", "EqualTo");
            addressFilter.put("Value", 0); //when this is zero address has not been checked yet...
            filters.put("ADDRESS_FILTER", addressFilter);

            ///exclude exited participants, if necessary
            addExitedFilter(filters, null);

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", null);
            queryData.put("Filters", filters);
            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new participant address validation query.", ex);
        }
        return queryId;
    }

    /**
     * Creates a query for new participant's with new participant data using date modified for comparison.
     */
    private String addParticipantSyncQuery(@NonNull String maxModifiedDate)
    {
        logger.info(LOG_PREFIX + "Adding participant sync query...");

        String queryId = null;
        try
        {
            Map<String, Object> filters = new HashMap<>();

            Map<String, Object> dateFilter = new HashMap<>();
            dateFilter.put("Variable", "DATSTAT_LASTMODIFIED");
            dateFilter.put("QueryOperator", "GreaterThanOrEqualTo");
            dateFilter.put("Value", maxModifiedDate);
            filters.put("DATE_FILTER", dateFilter);

            Map<String, Object> submissionSort = new HashMap<>();
            submissionSort.put("Variable", "DATSTAT_LASTMODIFIED");
            submissionSort.put("SortDirection", "Ascending");
            ArrayList sort = new ArrayList<>();
            sort.add(submissionSort);

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", sort);
            queryData.put("Filters", filters);
            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new participant sync query.", ex);
        }
        return queryId;
    }

    /**
     * Creates a query for missing kit requests of a particular type.
     *
     * @param requestIdField name of the kit request Id field in DatStat
     * @param validAddressRequired true if we need to filter results to only return participants with valid addresses
     * @param countries optionally filter for certain countries (only used when validAddressRequired is true)
     * @param done if we only want to generate kits for participants who are done
     */
    private String addParticipantsNeedingKitRequestIdQuery(@NonNull String requestIdField, @NonNull Boolean validAddressRequired, JsonArray countries, @NonNull Boolean done)
    {
        logger.info(LOG_PREFIX + "Adding kit request id query...");

        String queryId = null;
        try
        {
            Map<String, Object> kitFilter = new HashMap<>();
            kitFilter.put("Variable", requestIdField);
            kitFilter.put("QueryOperator", "EqualTo");
            kitFilter.put("Value", 0); //when this is zero a request Id has not been generated yet
            Map<String, Object> filters = new HashMap<>();
            filters.put("KIT_FILTER", kitFilter);

            StringBuilder addressFilterExpression = new StringBuilder("KIT_FILTER and ADDRESS_FILTER");

            //exclude exited participants, if necessary
            addExitedFilter(filters, addressFilterExpression);

            //see if we need to look for done participants only for this kit type
            if (done) {
                addDoneFilter(filters, addressFilterExpression);
            }

            if (validAddressRequired)
            {
                Map<String, Object> addressFilter = new HashMap<>();
                addressFilter.put("Variable", getDatStatAddressValidField());
                addressFilter.put("QueryOperator", "EqualTo");
                addressFilter.put("Value", 1);
                filters.put("ADDRESS_FILTER", addressFilter);

                //loop through list of acceptable countries, if none provided all are allowed
                if ((countries != null)&&(countries.size() > 0))
                {
                    for (int i = 0; i < countries.size(); i++)
                    {
                        if (i == 0)
                        {
                            addressFilterExpression.append(" and ");
                            if (countries.size() != 1) //more than one is acceptable
                            {
                                addressFilterExpression.append("(");
                            }
                        }
                        addQueryCountryFilter(filters, countries.get(i).getAsString());
                        addressFilterExpression.append(generateQueryCountryFilterName(countries.get(i).getAsString()));
                        if (countries.size() != 1)
                        {
                            addressFilterExpression.append(((i + 1) < countries.size()) ? " or " : ")");
                        }
                    }
                }
            }

            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", null);
            queryData.put("Filters", filters);

            if (validAddressRequired)
            {
                queryData.put("QueryFilterType", 2);
                queryData.put("ComplexExpression", addressFilterExpression.toString());// e.g., KIT_FILTER and ADDRESS_FILTER and (US_COUNTRY_FILTER or ...
            }
            else
            {
                queryData.put("QueryFilterType", 0);
                queryData.put("ComplexExpression", null);
            }

            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new participant kit request id query.", ex);
        }
        return queryId;
    }

    private void addExitedFilter(@NonNull Map<String, Object> filters, StringBuilder filterExpression) {
        if (getDatStatParticipantExitField() != null) {
            if (filterExpression != null) filterExpression.append(" and EXIT_FILTER");
            Map<String, Object> exitedFilter = new HashMap<>();
            exitedFilter.put("Variable", getDatStatParticipantExitField());
            exitedFilter.put("QueryOperator", "IsNull");
            filters.put("EXIT_FILTER", exitedFilter);
        }
    }

    private void addDoneFilter(@NonNull Map<String, Object> filters, StringBuilder filterExpression) {
        if (filterExpression != null) filterExpression.append(" and DONE_FILTER");
        Map<String, Object> doneFilter = new HashMap<>();
        doneFilter.put("Variable", datStatParticipantStatusField);
        doneFilter.put("QueryOperator", "EqualTo");
        doneFilter.put("Value", STATUS_DONE);
        filters.put("DONE_FILTER", doneFilter);
    }

    private void addQueryCountryFilter(@NonNull Map<String, Object> filters, @NonNull String country)
    {
        Map<String, Object> filter = new HashMap<>();
        filter.put("Variable", "DDP_COUNTRY");
        filter.put("QueryOperator", "EqualTo");
        filter.put("Value", country);
        filters.put(generateQueryCountryFilterName(country), filter);
    }

    private String generateQueryCountryFilterName(@NonNull String country)
    {
        return country + "_COUNTRY_FILTER";
    }

    /**
     * Sends requests to DatStat with the proper authentication information.
     */
    private HttpResponse sendSingleRequest(@NonNull Boolean resetValues, @NonNull MethodType method, @NonNull String additionalPath, HttpContent content) throws Exception
    {
        HttpResponse response = null;

        AuthSingleton singleton = AuthSingleton.getInstance(resetValues, datStatKey, datStatSecret, datStatUsername, datStatPassword, datStatUrl);

        OAuthParameters oauthParameters = new OAuthParameters();
        oauthParameters.signer = singleton.getSigner();
        oauthParameters.token = singleton.getToken();
        oauthParameters.consumerKey = datStatKey;

        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory(oauthParameters);
        GenericUrl genericUrl = new GenericUrl(generateCompleteApiUrl(datStatUrl, additionalPath));

        long startTime = System.currentTimeMillis();

        response = requestFactory.buildRequest(method.toString(), genericUrl, content).execute();

        DatStatUtil.preprocessDatStatResponse(response);

        return response;
    }

    /**
     * Creates recipient object from DatStat participant data.
     */
    private Recipient createRecipientUsingDatStat(@NonNull String participantId) throws IOException
    {
        Recipient recipient = new Recipient();

        HttpResponse response = sendRequest(MethodType.GET, String.format(PATH_GET_PARTICIPANT, derivedParticipantListId, participantId), null);

        JsonElement element = new JsonParser().parse(response.parseAsString());

        JsonObject json = element.getAsJsonObject();
        JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

        if (participantJson == null)
        {
            throw new NullValueException("Response ParticipantData is null.");
        }
        else
        {
            recipient.setFirstName(participantJson.get("DATSTAT_FIRSTNAME").getAsString());
            recipient.setLastName(participantJson.get("DATSTAT_LASTNAME").getAsString());
            recipient.setEmail(participantJson.get("DATSTAT_EMAIL").getAsString());
            recipient.setCurrentStatus(participantJson.get(datStatParticipantStatusField).getAsString());
            recipient.setId(participantJson.get(ALTPID_FIELD).getAsString());
            recipient.setShortId(participantJson.get("DDP_PARTICIPANT_SHORTID").getAsInt());
            recipient.setDateExited(participantJson.get(datStatParticipantExitField).isJsonNull() ? null : participantJson.get(datStatParticipantExitField).getAsString());
        }
        return recipient;
    }

    /**
     * Updates a particular notification field to "SENT" for a set of participants.
     *
     * @param notificationField name of the notification field in DatStat
     */
    private boolean updateParticipantNotificationStatus(@NonNull String notificationField, @NonNull Map<String, Recipient> participantList)
    {
        logger.info(LOG_PREFIX + "Updating participant notification status " + notificationField + " for participants...");
        Map<String, String> parameters = null;
        Map<String, Object> participantData = null;
        HttpResponse response = null;
        boolean success = true;

        for (Map.Entry<String, Recipient> entry : participantList.entrySet())
        {
            String participantId = entry.getKey();
            Recipient recipient = entry.getValue();

            try
            {
                parameters = new HashMap<>();
                parameters.put(notificationField, NotificationStatus.SENT.toString());
                participantData = new HashMap<>();
                participantData.put("ParticipantData", parameters);

                response = sendRequest(MethodType.POST, String.format(PATH_POST_PARTICIPANT_UPDATE, derivedParticipantListId, participantId), participantData);

                JsonElement element = new JsonParser().parse(response.parseAsString());
                JsonObject json = element.getAsJsonObject();
                JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

                if (participantJson == null)
                {
                    throw new NullValueException("Response ParticipantData is null.");
                }
            }
            catch (Exception ex)
            {
                success = false;
                //for now swallow and log errors for individual participant updates
                logger.error(LOG_PREFIX + "Unable to update participant notification status " + notificationField + " for participant " + recipient.getEmail() + ".", ex);
            }
        }

        return success;
    }

    /**
     * Add a kit request id of a particular type for a single participant.
     *
     * @param requestIdField name of the kit request Id field in DatStat
     */
    private void addParticipantKitRequestId(@NonNull String requestIdField, @NonNull String participantId)
    {
        logger.info(LOG_PREFIX + "Adding kit request id of type " + requestIdField + " for participant...");

        try
        {
            int requestId = Utility.getNextKitRequestId();
            Map<String, Integer> parameters = null;
            Map<String, Object> participantData = null;
            HttpResponse response = null;

            parameters = new HashMap<>();
            parameters.put(requestIdField, requestId);
            participantData = new HashMap<>();
            participantData.put("ParticipantData", parameters);

            response = sendRequest(MethodType.POST, String.format(PATH_POST_PARTICIPANT_UPDATE, derivedParticipantListId, participantId), participantData);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonObject json = element.getAsJsonObject();
            JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

            if (participantJson == null)
            {
                throw new NullValueException("Response ParticipantData is null.");
            }
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("Unable to add kit request id for participant.", ex);
        }
    }

    /**
     * Sets the ids for the derived and master participants lists so they can be used later.
     */
    private void configureParticipantListIds(@NonNull String derivedParticipantListName, @NonNull String masterParticipantListName)
    {
        logger.info(LOG_PREFIX + "Configuring participant list ids...");

        try
        {
            HttpResponse response = sendRequest(MethodType.GET, PATH_GET_PARTICIPANT_LISTS, null);

            JsonArray entries = (JsonArray)(new JsonParser().parse(response.parseAsString()));

            Properties data = null;
            derivedParticipantListId = null;

            for (JsonElement element : entries)
            {
                data = new Gson().fromJson(element, Properties.class);
                if (data.getProperty("Description").equals(derivedParticipantListName))
                {
                    derivedParticipantListId = getLastIdFromUri(data.getProperty("Uri"));
                }
                if (data.getProperty("Description").equals(masterParticipantListName))
                {
                    masterParticipantListId = getLastIdFromUri(data.getProperty("Uri"));
                }
            }

            if (derivedParticipantListId == null)
            {
                throw new NullValueException("Unable to find participant list: " + derivedParticipantListName);
            }

            if (masterParticipantListId == null)
            {
                throw new NullValueException("Unable to find participant list: " + masterParticipantListName);
            }

            logger.info(LOG_PREFIX + "Retrieved participant list ids.");
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to retrieve participant list ids.", ex);
        }
    }

    private void configureAnonSurveyNotifications(@NonNull String surveyJSON)
    {
        logger.info(LOG_PREFIX + "Configuring anonymous survey ids...");

        try
        {
            JsonArray propSurveys = (JsonArray)(new JsonParser().parse(surveyJSON));
            Map<String, Object> surveyData = null;

            //first build our hashmap using configuration properties
            for (JsonElement propSurveyElement : propSurveys)
            {
                surveyData = new HashMap<>();
                surveyData.put(DATSTAT_ID, "");

                surveyData.put(SURVEY_INFO, propSurveyElement);
                datStatAnonSurveyNotifications.put(propSurveyElement.getAsJsonObject().get("id").getAsString(), surveyData);
            }

            //now get all the survey ids from DatStat
            HttpResponse response = sendRequest(DatStatUtil.MethodType.GET, DatStatUtil.PATH_GET_SURVEYS, null);
            Properties datStatSurveyProperties = null;
            JsonArray datStatSurveys = (JsonArray)(new JsonParser().parse(response.parseAsString()));
            for (JsonElement datStatSurveyElement : datStatSurveys)
            {
                datStatSurveyProperties = new Gson().fromJson(datStatSurveyElement, Properties.class);

                surveyData = datStatAnonSurveyNotifications.get(datStatSurveyProperties.getProperty("Description"));

                //if the description for the survey element in DatStat matches a key in datStatAnonSurveyNotifications then proceed
                if (surveyData != null)
                {
                    //store the DatStat id for the survey so we can use it later
                    surveyData.put(DATSTAT_ID, getLastIdFromUri(datStatSurveyProperties.getProperty("Uri")));
                }
            }

            //check to see if we missed any survey ids, just in case
            for (String surveyName: datStatAnonSurveyNotifications.keySet())
            {
                if (datStatAnonSurveyNotifications.get(surveyName).get(DATSTAT_ID).toString().isEmpty())
                {
                    throw new DatStatRestApiException("Unable to find DatStat survey id for survey \"" + surveyName + "\".");
                }
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to retrieve anonymous survey ids.", ex);
        }
    }


    /**
     * Creates query needed to check for the existence of a participant using their email.
     */
    private String addParticipantExistenceQuery(@NonNull String datStatValue,@NonNull String datstatFieldName)
    {
        logger.info(LOG_PREFIX + "Adding participant query...");

        String queryId = null;
        try
        {
            Map<String, Object> fieldFilter = new HashMap<>();
            fieldFilter.put("Variable", datstatFieldName);
            fieldFilter.put("QueryOperator", 2);
            fieldFilter.put("Value", datStatValue);
            Map<String, Object> filter = new HashMap<>();
            filter.put("FILTER", fieldFilter);
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("Sort", null);
            queryData.put("Filters", filter);
            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_PARTICIPANT_QUERY_CREATE);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("Unable to add new participant query.", ex);
        }
        return queryId;
    }

    /**
     * Adds job to scheduler that will send out notifications to participants and create kit request ids.
     */
    private void createScheduledJobs(Scheduler scheduler, int jobIntervalInSeconds)
    {
        if ((scheduler != null)&&(jobIntervalInSeconds > 0))
        {
            try
            {
                //create job
                JobDetail job = JobBuilder.newJob(DatStatJob.class)
                        .withIdentity("DATSTAT_JOB", BasicTriggerListener.NO_CONCURRENCY_GROUP + ".PORTAL")
                        .build();

                //create trigger
                TriggerKey triggerKey = new TriggerKey("DATSTAT_TRIGGER", "PORTAL");
                SimpleTrigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .withSchedule(
                                simpleSchedule()
                                        .withIntervalInSeconds(jobIntervalInSeconds)
                                        .repeatForever()
                        ).build();

                //add job
                scheduler.scheduleJob(job, trigger);

                //add trigger listener
                scheduler.getListenerManager().addTriggerListener(new DatStatJobTriggerListener(), KeyMatcher.keyEquals(triggerKey));
            }
            catch (Exception ex)
            {
                throw new DatStatRestApiException("Unable to schedule DatStat job.", ex);
            }
        }
        else
        {
            logger.warn(LOG_PREFIX + "Background DatStat jobs will NOT be run.");
        }
    }

    public static String getLastIdFromUri(@NonNull String uri)
    {
        int location = uri.lastIndexOf('/');
        if (location != -1){ //found it
            return uri.substring(location + 1);
        } else return null;
    }

    public static void preprocessDatStatResponse(@NonNull HttpResponse response)
    {
        if ((response.getStatusCode() == 200) || (response.getStatusCode() == 201))
        {
            logger.info(LOG_PREFIX + "Response = success");
        }
        else
        {
            throw new DatStatRestApiException("Response = error; code = " + response.getStatusCode() + "; message = " + response.getStatusMessage());
        }
    }

    public static String generateCompleteApiUrl(@NonNull String baseUrl, @NonNull String additionalPath)
    {
        return baseUrl + PATH_API + additionalPath;
    }

    public String getExpectedSingleSurveySessionViaSurvey(@NonNull String survey, @NonNull String participantAltPid)
    {
        String surveySession = getSingleSurveySessionViaSurvey(survey, participantAltPid);

        if (surveySession == null) {
            throw new DatStatRestApiException("Unable to find survey session for participant = " + participantAltPid + ", survey = " + survey + ".");
        }

        return surveySession;
    }

    public String getSingleSurveySessionViaSurvey(String survey, String participantAltPid)
    {
        return getSingleSurveySessionViaUri(getSurveyConfigMap().get(survey).getSurveyDefinition().getUri(), participantAltPid);
    }

    /**
     * Returns the survey session
     * @param surveyURI
     * @param participantAltPid
     * @return
     */
    public String getSingleSurveySessionViaUri(String surveyURI, String participantAltPid){
        return getFirstSession(getSurveySessionsViaUri(surveyURI, participantAltPid, false, null));
    }

    public String getSingleFollowUpSurveySessionViaUri(String surveyURI, String participantAltPid, @NonNull String followUpSurveyInstance){
        return getFirstSession(getSurveySessionsViaUri(surveyURI, participantAltPid, false, followUpSurveyInstance));
    }

    private String getFirstSession(ArrayList<String> sessions) {
        if (sessions == null) {
            return null;
        }
        else {
            return sessions.get(0);
        }
    }

    /**
     * Returns the survey session
     * @param surveyURI
     * @param participantAltPid
     * @return
     */
    public ArrayList<String> getSurveySessionsViaUri(String surveyURI, String participantAltPid, boolean allowMultipleSessions) {
        return getSurveySessionsViaUri(surveyURI, participantAltPid, allowMultipleSessions, null);
    }

    /**
     * Returns the survey session
     * @param surveyURI
     * @param participantAltPid
     * @return
     */
    public ArrayList<String> getSurveySessionsViaUri(String surveyURI, String participantAltPid, boolean allowMultipleSessions,
                                                     String followUpSurveyInstance){
        logger.info(LOG_PREFIX + "Checking for survey session(s)...");

        String queryId = addSurveyQuery(participantAltPid, followUpSurveyInstance);

        ArrayList sessions = null;

        try {
            String surveyId = getLastIdFromUri(surveyURI);
            logger.info(LOG_PREFIX + "Execute query to find participant survey session id(s) for surveyURI " + surveyId + "...");
            HttpResponse response = sendRequest(DatStatUtil.MethodType.GET, String.format(DatStatUtil.PATH_GET_RESULTS_USING_QUERY, surveyId, queryId), null);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonArray resultList = element.getAsJsonObject().get("Data").getAsJsonArray();

            logger.info(LOG_PREFIX + "Total participant survey session ids for surveyURI " + surveyId + " = " + resultList.size() + ".");

            if ((!allowMultipleSessions)&&(resultList.size() > 1)) {
                throw new DatStatRestApiException("Too many survey sessions found for participant " + participantAltPid);
            }
            else if (resultList.size() > 0) {
                sessions = new ArrayList();
                for (JsonElement resultElement : resultList) {
                    JsonArray resultItems = resultElement.getAsJsonArray();
                    sessions.add(resultItems.get(0).getAsString());
                }
            }
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("An error occurred trying to get survey session(s) for a participant.", ex);
        }
        return sessions;
    }

    public HashMap<String, String> getSingleSurveyInfoViaSurvey(String survey, String participantAltPid, String followUpSurveyInstance) {
        logger.info(LOG_PREFIX + "Checking for survey info...");

        String queryId = addSurveyQuery(participantAltPid, followUpSurveyInstance);

        HashMap<String, String> returnMap = null;

        try {
            String surveyId = getLastIdFromUri(getSurveyConfigMap().get(survey).getSurveyDefinition().getUri());
            logger.info(LOG_PREFIX + "Execute query to find participant survey info for surveyURI " + surveyId + "...");
            HttpResponse response = sendRequest(DatStatUtil.MethodType.GET, String.format(DatStatUtil.PATH_GET_RESULTS_USING_QUERY, surveyId, queryId), null);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonArray resultList = element.getAsJsonObject().get("Data").getAsJsonArray();;

            if (resultList.size() > 1) {
                throw new DatStatRestApiException("Too many survey sessions found for participant " + participantAltPid);
            }
            else if (resultList.size() == 1) {
                returnMap = new HashMap<>();
                String url = portalWorkflowNotificationLookup.get(survey).getAsJsonObject().get("url").getAsString();

                for (JsonElement resultElement : resultList) {
                    JsonArray resultItems = resultElement.getAsJsonArray();
                    returnMap.put(AbstractRequestHandler.JSON_PARTICIPANT_ID, participantAltPid);
                    returnMap.put(AbstractRequestHandler.JSON_SURVEY_URL, generateSurveyUrlForLocalUI(url, survey, resultItems.get(0).getAsString()) + "/" + FollowUpSurveyHandler.ADD_NOW_CODE);
                    returnMap.put(AbstractRequestHandler.JSON_STATUS, AbstractSurveyInstance.getSubmissionStatus(resultItems.get(1).getAsInt()).toString());
                }
            }
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("An error occurred trying to get survey info for a participant.", ex);
        }
        return returnMap;
    }

    /**
     * Creates a query to get the survey session id for a participant.
     */
    private String addSurveyQuery(String participantAltPid, String followUpSurveyInstance) {
        logger.info(LOG_PREFIX + "Adding survey query...");

        String queryId = null;
        try {
            //first create a result query
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("IncludeRawData", true);
            queryData.put("IncludeSummaryData", false);
            queryData.put("IncludePartialData", true);
            queryData.put("TestDataOnly", false);
            queryData.put("TestSurvey", false);
            queryData.put("Variables", new String[] {"DATSTAT.SESSIONID", "DATSTAT.SUBMISSIONSTATUS"});

            Map<String, Object> altpidFilter = new HashMap<>();
            altpidFilter.put("Variable", ALTPID_FIELD);
            altpidFilter.put("QueryOperator", "EqualTo");
            altpidFilter.put("Value", participantAltPid);
            Map<String, Object> filter = new HashMap<>();
            filter.put("DATSTAT_ALTPID_FILTER", altpidFilter);

            if (followUpSurveyInstance != null) {
                Map<String, Object> followUpFilter = new HashMap<>();
                followUpFilter.put("Variable", "DDP_FOLLOWUP");
                followUpFilter.put("QueryOperator", "EqualTo");
                followUpFilter.put("Value", followUpSurveyInstance);
                filter.put("DDP_FOLLOWUP_FILTER", followUpFilter);
            }

            queryData.put("Filters", filter);

            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_RESULT_QUERY_CREATE);
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("Unable to add new survey query.", ex);
        }
        return queryId;
    }

    /**
     * Creates a query to get basic survey info.
     */
    private String addSurveyInfoQuery() {
        logger.info(LOG_PREFIX + "Adding survey info query...");

        String queryId = null;
        try {
            //first create a result query
            Map<String, Object> queryData = new HashMap<>();
            queryData.put("IncludeRawData", true);
            queryData.put("IncludeSummaryData", false);
            queryData.put("IncludePartialData", true);
            queryData.put("TestDataOnly", false);
            queryData.put("TestSurvey", false);
            queryData.put("Variables", new String[] {ALTPID_FIELD, "DDP_FOLLOWUP", "DATSTAT.SUBMISSIONSTATUS", "DDP_PARTICIPANT_SHORTID"});

            queryData.put("Filters", null);

            queryData.put("QueryFilterType", 0);
            queryData.put("ComplexExpression", null);
            queryData.put("Uri", null);

            queryId = addQuery(queryData, PATH_POST_RESULT_QUERY_CREATE);
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("Unable to add new survey info query.", ex);
        }
        return queryId;
    }

    public Recipient getSimpleParticipantInfoById(@NonNull String participantId) {
        logger.info(LOG_PREFIX + "finding participant info by participant id...");

        Recipient recipient = null;

        try
        {
            recipient = createRecipientUsingDatStat(participantId);
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to find a participant info by participantId " + participantId, ex);
        }

        return recipient;
    }

    public Recipient getSimpleParticipantInfoByAltPid(@NonNull String participantAltPid) {
        logger.info(LOG_PREFIX + "finding participant info by altpid...");

        Recipient recipient = null;

        try
        {
            String participantId = getParticipantIdByAltPid(participantAltPid);

            if (participantId != null)
            {
                recipient = createRecipientUsingDatStat(participantId);
            }
        }
        catch (Exception ex)
        {
            throw new DatStatRestApiException("An error occurred trying to find a participant info by altpid " + participantAltPid, ex);
        }

        return recipient;
    }

    /**
     * Updates fields for a participant.
     *
     * @param altPid altPid of the participant
     * @param updateInfo map of participantData to update
     */
    public void updateParticipantDataViaAltPid(@NonNull String altPid, @NonNull Map<String, Object> updateInfo) {
        logger.info(LOG_PREFIX + "Updating data for participant " + altPid + " ...");

        updateParticipantDataViaId(getParticipantIdByAltPid(altPid), updateInfo);
    }

    /**
     * Updates the exit field for a participant.
     *
     * @param participantId of the participant
     */
    public void setParticipantExitedViaId(@NonNull String participantId) {
        HashMap<String, Object> changes = new HashMap<>();
        changes.put(datStatParticipantExitField, Utility.getCurrentUTCDateTimeAsString());

        try {
            updateParticipantDataViaId(participantId, changes);
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("Unable to set exit field for participant.", ex);
        }
    }

    /**
     * Updates fields for a participant.
     *
     * @param participantId of the participant
     * @param updateInfo map of participantData to update
     */
    public void updateParticipantDataViaId(@NonNull String participantId, @NonNull Map<String, Object> updateInfo) {
        try {
            Map<String, Object> participantData = new HashMap<>();
            participantData.put("ParticipantData", updateInfo);

            HttpResponse response = sendRequest(MethodType.POST, String.format(PATH_POST_PARTICIPANT_UPDATE, derivedParticipantListId, participantId), participantData);

            JsonElement element = new JsonParser().parse(response.parseAsString());
            JsonObject json = element.getAsJsonObject();
            JsonObject participantJson = json.get("ParticipantData").getAsJsonObject();

            if (participantJson == null) {
                throw new NullValueException("Response ParticipantData is null.");
            }
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("Unable to update participant data for participant.", ex);
        }
    }

    public <T extends SurveyInstance> Map<String, Class<T>> getAllSurveySessionsForParticipant(String participantAltPid) {
        Map<String, Class<T>> sessions = new HashMap<>();

        for (JsonElement surveyInfo : portalSurveyLookup.values()) {
            if (!(surveyInfo.getAsJsonObject().get("anonymous").getAsBoolean())) {

                SurveyConfig config = surveyConfigMap.get(surveyInfo.getAsJsonObject().get("surveyPath").getAsString());

                ArrayList<String> sessionIds = getSurveySessionsViaUri(config.getSurveyDefinition().getUri(), participantAltPid, config.getFollowUpType().equals(SurveyConfig.FollowUpType.REPEATING));
                if (sessionIds != null) {
                    for (String sessionId : sessionIds) {
                        sessions.put(sessionId, config.getSurveyClass());
                    }
                }
            }
        }

        return sessions;
    }

    public String getDatStatParticipantStatusField() {
        return datStatParticipantStatusField;
    }

    public String getDatStatAddressCheckField() {
        return datStatAddressCheckField;
    }

    public String getDatStatAddressValidField() {
        return datStatAddressValidField;
    }

    public String getDatStatParticipantExitField() {return datStatParticipantExitField; }

    public Map<String, SurveyConfig> getSurveyConfigMap() {
        return surveyConfigMap;
    }

    public Recipient getSimpleParticipantInfoByEmail(String email) {
        logger.info(LOG_PREFIX + "finding participant info...");

        Recipient recipient = null;

        try {
            String participantId = getParticipantIdByEmail(email);

            if (participantId != null) {
                recipient = createRecipientUsingDatStat(participantId);
            }
        }
        catch (Exception ex) {
            throw new DatStatRestApiException("An error occurred trying to find a participant info by altpid.", ex);
        }
        return recipient;
    }

    private void reset() {
        environment = null;
        portalFrontendUrl = null;
        portalFrontendGenericSurvey = null;
        derivedParticipantListId = null;
        masterParticipantListId = null;
        additionalAttempts = 0;
        sleepInMs = 500;
        portalSurveyUI = true;
        datStatUrl = null;
        datStatParticipantFirstSurveyUrl = null;
        datStatKey = null;
        datStatSecret = null;
        datStatUsername = null;
        datStatPassword = null;
        datStatParticipantStatusField = null;
        datStatParticipantExitField = null;
        datStatAddressValidField = null;
        datStatAddressCheckField = null;
        portalNotificationAttachmentLookup = new HashMap<>();
        datStatParticipantWorkflowNotifications = null;
        portalWorkflowNotificationLookup = new HashMap<>();
        portalReminderNotificationLookup = new HashMap<>();
        portalSurveyLookup = new HashMap<>();
        portalNextSurveyLookup = new HashMap<>();
        kitRequests = null;
        currentNotificationLookup = new HashMap<>();
        datStatAnonSurveyNotifications = new HashMap<>();
        datStatGenerateTestParticipants = false;
        emailClassName = null;
        emailKey = null;
        emailClientSettings = null;
        invalidAddressRecipient = null;
        invalidAddressSubject = null;
        started = false;
        surveyConfigMap = null;
        institutionSurveyId = null;
    }
}
