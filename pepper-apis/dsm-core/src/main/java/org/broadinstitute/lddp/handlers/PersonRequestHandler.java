package org.broadinstitute.lddp.handlers;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.email.EmailRecord;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.exception.NullValueException;
import org.broadinstitute.lddp.handlers.util.Person;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the processing of survey and mailing list requests.
 */
public class PersonRequestHandler extends AbstractRequestHandler<Person>
{
    private static final Logger logger = LoggerFactory.getLogger(PersonRequestHandler.class);

    private static final String LOG_PREFIX = "PROCESS PERSON REQUEST - ";

    public static final String LOVED_ONE_SURVEY = "LOVED_ONE";

    public static final String JSON_ACTIONTYPE = "actionType";

    public static final String JSON_VALUE = "value";

    public static final String JSON_TOKEN = "ddpToken";

    private boolean splashPageOnly;

    public enum ResultType {
        EMAILED_PARTICIPANT, EMAILED_CONTACT, PARTICIPANT_CREATED, LOVED_ONE_SURVEY, PARTICIPANT_URL
    }

    public enum MailingListNotification {
        JOIN, ALREADY_JOINED, LEFT
    }

    private Map<String, JsonElement> mailingListNotifications = new HashMap<>();
    private String csrfCookieName;
    private String secret;
    private String salt;

    public PersonRequestHandler(EDCClient edc, Config config) {
        super(Person.class, edc, config);
        setup();
    }

    public PersonRequestHandler(Config config) {
        super(Person.class, config);
        splashPageOnly = true;
        setup();
    }

    @Override
    protected Result processRequest(@NonNull Person person, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        logger.info(LOG_PREFIX + "Starting...");

        try
        {
            if (!splashPageOnly) {
                //if person wants to complete loved one survey don't perform any other checks...
                if (person.getPersonType() == Person.PersonType.LOVED_ONE)
                {
                    return processLovedOne();
                }
                else {
                    //no matter what person entered into the form, first check if this person is a participant already
                    if (edc.participantExists(person.getEmail())) {
                        return processExistingParticipant(person);
                    }
                    //person isn't a participant and would like to become a contact for the mailing list...
                    else if (person.getPersonType() == Person.PersonType.CONTACT) {
                        return processContact(person);
                    }
                    //person isn't already a participant and wants to become a new participant
                    else {
                        return processNewParticipant(response, person);
                    }
                }
            }
            else { //just do the mailing list
                if (person.getPersonType() == Person.PersonType.CONTACT) {
                    return processContact(person);
                }
                else {
                    throw new RuntimeException(person.getPersonType() + " is invalid.");
                }
            }
        }
        catch (Exception ex) {
            logger.error(LOG_PREFIX + "error: ", ex);
            return new Result(500);
        }
    }

    private void sendContactEmail(@NonNull Person person, @NonNull MailingListNotification notificationType) {
        JsonElement notificationInfo = mailingListNotifications.get(notificationType.toString());

        if (notificationInfo == null) {
            throw new NullValueException("Unable to find correct mailing list notification.");
        }

        Recipient recipient = new Recipient(person.getFirstName(), person.getLastName(), person.getEmail());

        EmailRecord.add(notificationInfo, null, recipient,null, recipient.getEmail());
    }

    public static String generateParticipantCreatedResult(@NonNull String participantAltPid, @NonNull EDCClient edc) {
        return ResultType.PARTICIPANT_CREATED + "=" + edc.generateParticipantFirstSurveyUrl(participantAltPid);
    }

    public static String generateLovedOneSurveyResult(@NonNull EDCClient edc, @NonNull Config config) {
        return ResultType.LOVED_ONE_SURVEY + "=" + edc.generateAnonSurveyUrl(LOVED_ONE_SURVEY);
    }

    private void setup() {
        if (!splashPageOnly) {
            this.csrfCookieName = config.getString("portal.csrfCookieName");
            this.secret = config.getString("portal.jwtSecret");
            this.salt = config.getString("portal.jwtSalt");
        }

        JsonArray notifications = (JsonArray)(new JsonParser().parse(config.getString("mailingListNotifications")));
        for (JsonElement notificationInfo : notifications)
        {
            mailingListNotifications.put(notificationInfo.getAsJsonObject().get("id").getAsString(), notificationInfo);
        }
    }

    private Result processLovedOne() {
        logger.info(LOG_PREFIX + "Send to loved one survey.");

        if (config.getBoolean("portal.portalSurveyUI")) {
            throw new RuntimeException(Person.PersonType.LOVED_ONE + " is invalid.");
        }

        Map<String, String> returnMap = generateReturnMap();

        //go ahead and redirect user, they are not a participant
        returnMap.put(JSON_ACTIONTYPE, ResultType.PARTICIPANT_URL.toString());
        returnMap.put(JSON_VALUE, generateLovedOneSurveyResult(edc, config));
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
    }

    private Result processExistingParticipant(Person person) {
        logger.info(LOG_PREFIX + "Participant already exists.");

        Map<String, String> returnMap = generateReturnMap();

        edc.sendEmailToParticipant(person.getEmail());
        returnMap.put(JSON_ACTIONTYPE, ResultType.EMAILED_PARTICIPANT.toString());
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
    }

    private Result processContact(Person person) {
        //check to see if they are already on the mailing list so we don't add them twice
        if (!person.contactExistsInMailingList()) {
            logger.info(LOG_PREFIX + "Contact is new.");
            person.addContactToMailingList();
            sendContactEmail(person, MailingListNotification.JOIN);
        }
        else sendContactEmail(person, MailingListNotification.ALREADY_JOINED);

        logger.info(LOG_PREFIX + "Contact email sent.");

        Map<String, String> returnMap = generateReturnMap();

        returnMap.put(JSON_ACTIONTYPE, ResultType.EMAILED_CONTACT.toString());
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
    }

    private Result processNewParticipant(spark.Response response, Person person) throws Exception {
        logger.info(LOG_PREFIX + "Participant is new.");

        Map<String, String> returnMap = generateReturnMap();

        //if this person was on the mailing list and wants to be a participant we will remove them from the mailing list first
        if (person.contactExistsInMailingList()) {
            logger.info(LOG_PREFIX + "Participant exists as contact.");
            person.removeContactFromMailingList();
        }

        String idToken = null;

        //create Auth0 account
        if (auth0Util != null) {
            logger.info(LOG_PREFIX + "Auth0 signup starting...");
            if (person.getPassword().isEmpty()) {
                throw new RuntimeException("Password required for Auth0 signup for " + person.getEmail());
            }
            idToken = auth0Util.signUpAndGetIdToken(person.getEmail(), person.getPassword());
            if (idToken == null) {
                throw new RuntimeException("Auth0 signup failure for " + person.getEmail());
            }
        }

        String participantAltPid = edc.addParticipant(person, config.getString("datStat.derivedParticipantList"));

        if (config.getBoolean("portal.portalSurveyUI")) {
            returnMap.put(JSON_ACTIONTYPE, ResultType.PARTICIPANT_CREATED.toString());
            returnMap.put(JSON_VALUE, participantAltPid);

            if (idToken != null) {
                //take Auth0Tokens retrieved from creating account and use them to create ddpToken
                String ddpToken = SecurityHelper.createAccessTokenAndCookieFromAuth0Tokens(response, secret, salt, csrfCookieName,
                        (DatStatUtil) edc, auth0Util, idToken, true );
                returnMap.put(JSON_TOKEN, ddpToken);
            }

            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
        }

        returnMap.put(JSON_ACTIONTYPE, ResultType.PARTICIPANT_URL.toString());
        returnMap.put(JSON_VALUE, generateParticipantCreatedResult(participantAltPid, edc));
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
    }

    private Map<String, String> generateReturnMap() {
        Map<String, String> returnMap = new HashMap<>();
        returnMap.put(JSON_ACTIONTYPE, null);
        returnMap.put(JSON_VALUE, null);
        return returnMap;
    }
}

