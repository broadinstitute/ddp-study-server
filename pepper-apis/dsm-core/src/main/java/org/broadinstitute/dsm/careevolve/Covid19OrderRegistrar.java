package org.broadinstitute.dsm.careevolve;

import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.CareEvolveException;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Places orders with CareEvolve for COVID-19 virology
 */
public class Covid19OrderRegistrar {

    public static final String ADDRESS_FIELD = "address";
    public static final String PROFILE_FIELD = "profile";
    public static final String GUID_FIELD = "guid";
    public static final String FIRST_NAME_FIELD = "firstName";
    public static final String LAST_NAME_FIELD = "lastName";
    public static final String ACTIVITIES_FIELD = "activities";
    public static final String ANSWER_FIELD = "answer";
    public static final String BASELINE_COVID_ACTIVITY = "BASELINE_COVID";
    private static final Logger logger = LoggerFactory.getLogger(Covid19OrderRegistrar.class);
    private final String endpoint;
    private final String careEvolveAccount;
    private final Provider provider;
    private final int maxRetries;
    private final long retryWaitMillis;
    private RestHighLevelClient esClient;

    /**
     * Create a new one that uses the given endpoint
     * for placing orders
     */
    public Covid19OrderRegistrar(String endpoint,
                                 String careEvolveAccount,
                                 Provider provider,
                                 int maxRetries,
                                 int retryWaitSeconds) {
        this.endpoint = endpoint;
        this.careEvolveAccount = careEvolveAccount;
        this.provider = provider;
        this.maxRetries = maxRetries;
        this.retryWaitMillis = retryWaitSeconds * 1000;
    }

    public static JsonObject getBaselineCovidActivity(JsonArray activities) {
        JsonObject baselineCovidActivity = null;
        for (int i = 0; i < activities.size(); i++) {
            JsonObject activity = activities.get(i).getAsJsonObject();
            if (BASELINE_COVID_ACTIVITY.equals(activity.get("activityCode").getAsString())) {
                baselineCovidActivity = activity;
            }
        }
        return baselineCovidActivity;
    }

    public static Patient fromElasticData(JsonObject data) {
        Patient patient;
        if (data != null) {
            JsonObject address = data.get(ADDRESS_FIELD).getAsJsonObject();
            Address careEvolveAddress = Address.fromElasticAddress(address);

            JsonObject profile = data.get(PROFILE_FIELD).getAsJsonObject();
            String patientId = profile.get(GUID_FIELD).getAsString();

            String firstName = profile.get(FIRST_NAME_FIELD).getAsString();
            String lastName = profile.get(LAST_NAME_FIELD).getAsString();

            JsonArray activities = data.get(ACTIVITIES_FIELD).getAsJsonArray();

            JsonObject baselineCovidActivity = Covid19OrderRegistrar.getBaselineCovidActivity(activities);

            JsonArray baselineCovidAnswers = baselineCovidActivity.get("questionsAnswers").getAsJsonArray();
            String dob = null;

            // mappings  for  race, ethnicity, and  sex are custom  for the  Ellkay API
            // and are controlled  by the Ellkay integration
            String race = "2131-1"; // code for "other"
            String ethnicity = "U";
            String sex = "U";
            for (int i = 0; i < baselineCovidAnswers.size(); i++) {
                JsonObject answer = baselineCovidAnswers.get(i).getAsJsonObject();
                String questionStableId = answer.get("stableId").getAsString();

                if ("DOB".equals(questionStableId)) {
                    JsonObject dateFields = answer.get("dateFields").getAsJsonObject();
                    StringBuilder dobStr = new StringBuilder();
                    dobStr.append(dateFields.get("year")).append("-")
                            .append(dateFields.get("month")).append("-")
                            .append(dateFields.get("day"));
                    SimpleDateFormat dobFormat = new SimpleDateFormat("yyyy-MM-dd");
                    try {
                        dob = dobFormat.format(dobFormat.parse(dobStr.toString()));
                    } catch (ParseException e) {
                        throw new CareEvolveException("Could not parse dob " + dobStr.toString()
                                + " for participant " + patientId + " in activity instance " + baselineCovidActivity.get("guid"));
                    }

                } else if ("SEX".equals(questionStableId)) {
                    JsonArray sexAnswers = answer.getAsJsonArray(ANSWER_FIELD);
                    if (sexAnswers.size() > 0) {
                        sex = sexAnswers.get(0).getAsString();
                    }

                    if ("SEX_FEMALE".equals(sex)) {
                        sex = "F";
                    } else if ("SEX_MALE".equals(sex)) {
                        sex = "M";
                    } else if ("OTHER".equals(sex)) {
                        sex = "U";
                    } else {
                        logger.error("Could not map sex " + sex + " to Ellkay code; will default to unknown for " + baselineCovidActivity.get("guid"));
                    }

                } else if ("RACE".equals(questionStableId)) {
                    JsonArray races = answer.getAsJsonArray(ANSWER_FIELD);

                    if (races.size() == 1) {
                        // CareEvolve only supports a  single race per order
                        race = races.get(0).getAsString();

                        if ("ASIAN".equals(race)) {
                            race = "2028-9";
                        } else if ("BLACK".equals(race)) {
                            race = "2054-5";
                        } else if ("AMERICAN_INDIAN".equals(race)) {
                            race = "2054-5";
                        } else if ("NATIVE_HAWAIIAN".equals(race)) {
                            race = "2076-8";
                        } else if ("WHITE".equals(race)) {
                            race = "2106-3";
                        } else if ("OTHER".equals(race)) {
                            race = "2131-1";
                        } else {
                            logger.error("Could not map race " + race + " to Ellkay code; will default to other for " + baselineCovidActivity.get("guid"));
                        }
                    } else {
                        // if there's no value or if multiple values are selected,
                        // use the code for "other"
                        race = "2131-1";
                    }
                } else if ("ETHNICITY".equals(questionStableId)) {
                    JsonArray answers = answer.getAsJsonArray(ANSWER_FIELD);
                    if (answers.size() > 0) {
                        ethnicity = answer.getAsJsonArray(ANSWER_FIELD).get(0).getAsString();
                        if ("HISPANIC_LATINO".equals(ethnicity)) {
                            ethnicity = "H";
                        } else if ("NOT_HISPANIC_LATINO".equals(ethnicity)) {
                            ethnicity = "N";
                        } else {
                            logger.error("Could not map ethnicity " + ethnicity + " to Ellkay code; will default to unknown for " + baselineCovidActivity.get("guid"));
                        }
                    }
                }
            }
            patient = new Patient(patientId, firstName, lastName, dob, race, ethnicity, sex, careEvolveAddress);
        } else {
            throw new IllegalArgumentException("data must be non-null");
        }
        return patient;
    }

    public OrderResponse orderTest(Authentication auth, Patient participant, String kitLabel, String kitId, Instant kitPickupTime) throws CareEvolveException {
        String patientId = participant.getPatientId();
        if (!(participant.hasFullName() && participant.hasDateOfBirth() && participant.hasAddress())) {
            throw new CareEvolveException("Cannot place order with incomplete data for " + patientId);
        }
        List<AOE> aoes = AOE.forTestBoston(patientId, kitId);
        Message message = new Message(new Order(careEvolveAccount, participant, kitLabel, kitPickupTime, provider, aoes), kitId);

        OrderResponse orderResponse = null;

        boolean orderSucceeded = false;
        Collection<Exception> orderExceptions = new ArrayList<>();
        int numAttempts = 0;
        do {
            try {
                orderResponse = orderTest(auth, message);
                orderSucceeded = true;
                logger.info("Placed CE order {} {} for Participant {}", orderResponse.getHandle(), orderResponse.getHl7Ack(),
                        participant.getPatientId());
            } catch (IOException e) {
                orderExceptions.add(e);
                logger.warn("Could not order test for " + patientId + ".  Pausing for " + retryWaitMillis + "ms before retry " + numAttempts + "/" + maxRetries, e);
                try {
                    Thread.sleep(retryWaitMillis);
                } catch (InterruptedException interruptedException) {
                    logger.error("Interrupted while waiting for CE order retry for patient " + patientId, e);
                }
            }
            numAttempts++;
        } while (numAttempts < maxRetries && !orderSucceeded);

        if (!orderSucceeded) {
            String exceptionsText = StringUtils.join(orderExceptions, "\n");
            throw new CareEvolveException("Could not order test for " + patientId + " after " + maxRetries + ":\n" + exceptionsText);
        }

        if (StringUtils.isNotBlank(orderResponse.getError())) {
            throw new CareEvolveException("Order for participant " + patientId + " with handle  " + orderResponse.getHandle() + " placed "
                    + "with error " + orderResponse.getError());
        }
        return orderResponse;
    }

    /**
     * Places an order in CareEvolve
     *
     * @param auth             the API credentials
     * @param participantHruid hruid for the participant
     * @param kitLabel         The label on the swab.  Corresponds to ORC-2 and GP sample_id
     * @param kitId            an identifier that will show up in Birch to help
     *                         associate the result back to the proper kit
     * @param kitPickupTime    the time at which the kit was picked
     * @param cfg
     */
    public OrderResponse orderTest(Authentication auth, String participantHruid, String kitLabel,
                                   String kitId, Instant kitPickupTime, Connection conn, Config cfg) throws CareEvolveException {
        if (kitPickupTime == null) {
            throw new CareEvolveException("Cannot place order for " + kitLabel + " without a pickup time");
        }
        try {
            esClient = ElasticSearchUtil.getClientForElasticsearchCloudCF(cfg.getString(ApplicationConfigConstants.ES_URL),
                    cfg.getString(ApplicationConfigConstants.ES_USERNAME), cfg.getString(ApplicationConfigConstants.ES_PASSWORD),
                    cfg.getString(ApplicationConfigConstants.ES_PROXY));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not initialize es client", e);
        }
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole("testboston", DBConstants.HAS_KIT_REQUEST_ENDPOINTS, conn);
        Map<String, Map<String, Object>> esData = ElasticSearchUtil.getSingleParticipantFromES(ddpInstance.getName(),
                ddpInstance.getParticipantIndexES(), esClient, participantHruid);

        if (esData.size() == 1) {
            JsonObject data = new JsonParser().parse(new Gson().toJson(esData.values().iterator().next())).getAsJsonObject();
            if (data != null) {
                Patient patient = fromElasticData(data);
                return orderTest(auth, patient, kitLabel, kitId, kitPickupTime);
            } else {
                throw new CareEvolveException("No participant data for " + participantHruid + ".  Cannot register order.");
            }
        } else {
            throw new CareEvolveException("No participant data found for " + participantHruid + ".  Cannot register order.");
        }
    }

    /**
     * Order a test using the given auth and message details.
     * Returns an {@link OrderResponse} when the order has been
     * placed successfully.  Otherwise, an exception is thrown.
     */
    private OrderResponse orderTest(Authentication auth, Message message) throws IOException {
        logger.info("About to send {} as {} to {}", message.getName(), endpoint);
        AuthenticatedMessage careEvolveMessage = new AuthenticatedMessage(auth, message);

        String json = new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(careEvolveMessage);

        Response response = Request.Post(endpoint).bodyString(json, ContentType.APPLICATION_JSON).execute();
        HttpResponse httpResponse = response.returnResponse();

        String responseString = EntityUtils.toString(httpResponse.getEntity());

        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            OrderResponse orderResponse = new Gson().fromJson(IOUtils.toString(httpResponse.getEntity().getContent()), OrderResponse.class);
            return orderResponse;
        } else {
            logger.error("Order {} returned {} with {}", message.getName(), httpResponse.getStatusLine().getStatusCode(), responseString);
            throw new CareEvolveException("CareEvolve returned " + httpResponse.getStatusLine().getStatusCode() + " with " + responseString);
        }
    }
}
