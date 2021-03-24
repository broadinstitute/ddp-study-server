package org.broadinstitute.ddp.datstat;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.Key;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.NonNull;
import org.broadinstitute.ddp.email.Recipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Place to put methods that do basic CRUD for survey sessions.
 */
public class SurveyService {

    private static final Logger logger = LoggerFactory.getLogger(SurveyService.class);

    public static final String SURVEY_SESSIONS_PATH = "/surveysessions";

    /**
     * Fetches the list of surveys currently available
     */
    public Collection<SurveyDefinition> fetchSurveys(DatStatUtil datstatUtil) {
        HttpResponse response = datstatUtil.sendRequest(DatStatUtil.MethodType.GET, DatStatUtil.PATH_GET_SURVEYS, null);

        try {
            String surveysString = response.parseAsString();

            SurveyDefinition[] surveyDefs = new Gson().fromJson(surveysString, SurveyDefinition[].class);
            List<SurveyDefinition> surveyDefinitions = Arrays.asList(surveyDefs);
            return surveyDefinitions;
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't fetch surveys ", e);
        }
    }

    public <T extends SurveyInstance> T fetchSurveyInstance(DatStatUtil datstatUtil,
                                                               Class<T> surveyInstanceClass,
                                                               String surveySessionId) {
        String sessionUri = SURVEY_SESSIONS_PATH + "/" + surveySessionId;
        HttpResponse getResponse = datstatUtil.sendRequest(DatStatUtil.MethodType.GET, SURVEY_SESSIONS_PATH + "/" + surveySessionId, null);

        if (getResponse != null) {
            try {
                SurveySessionRequest surveySessionRequest = new Gson().fromJson(getResponse.parseAsString(), SurveySessionRequest.class);
                Gson gson = new Gson();
                if (surveySessionRequest.sessionData.size() != 1) {
                    throw new RuntimeException("We have " + surveySessionRequest.sessionData.size() + " survey sessions for " + surveySessionId);
                }
                T surveyInstance = gson.fromJson(gson.toJson(surveySessionRequest.sessionData.iterator().next()), surveyInstanceClass);

                return setupInstance(surveyInstance, new SurveySession(sessionUri), null);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't fetch survey instance ", e);
            }
        }
        else {
            return null;
        }
    }

    /**
     * Creates a new "instance" of a survey (aka survey session).
     * When a client decides they want to take a particular survey,
     * call this method to create an instance of the survey for them.
     */
    public <T extends SurveyInstance> T createSurveyInstance(DatStatUtil datstatUtil,
                                                                SurveyDefinition surveyDef,
                                                                Class<T> surveyInstanceClass,
                                                                @NonNull Recipient recipient,
                                                                HashMap<String, Object> payload,
                                                                String followUpInstance) {
        logger.info("Creating survey instance for " + surveyDef.getDescription());

        try {
            // create a new survey session
            SurveySessionRequest<T> surveySessionRequest = new SurveySessionRequest(newSurveyInstance(surveyInstanceClass, recipient, payload, followUpInstance));
            HttpResponse postResponse = datstatUtil.sendRequest(DatStatUtil.MethodType.POST, surveyDef.getUri() + SURVEY_SESSIONS_PATH, surveySessionRequest);

            SurveySession newSession = new Gson().fromJson(postResponse.parseAsString(), SurveySession.class);

            T surveyInstance = fetchSurveyInstance(datstatUtil, surveyInstanceClass, newSession.getSessionId());

            surveyInstance.runCreationPostProcessing(recipient, newSession.getSessionId(), datstatUtil);

            return surveyInstance;
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create survey instance ", e);
        }
    }

    public <T extends SurveyInstance> T createSurveyInstance(DatStatUtil datstatUtil,
                                                             SurveyDefinition surveyDef,
                                                             Class<T> surveyInstanceClass,
                                                             @NonNull Recipient recipient,
                                                             HashMap<String, Object> payload) {
        return createSurveyInstance(datstatUtil, surveyDef, surveyInstanceClass, recipient, payload,null);
    }

    private <T extends SurveyInstance> T setupInstance(T surveyInstance, SurveySession newSession, String altPid) {
        surveyInstance.setSurveySession(newSession);
        if (altPid != null) {
            surveyInstance.setAltPid(altPid);
        }
        surveyInstance.onDatstatLoadComplete();
        surveyInstance.setSurveyReadOnlyStatus();

        return surveyInstance;
    }

    public SurveyInstance newSurveyInstance(Class surveyInstanceClass, Recipient recipient, HashMap<String, Object> payload, String followUpInstance) throws Exception {
        SurveyInstance survey = (SurveyInstance)surveyInstanceClass.newInstance();
        survey.initialize(recipient, followUpInstance);
        survey.applyChangeMap(payload);
        return survey;
    }

    /**
     * Saves a survey instance back to datstat
     */
    public void saveSurveyInstance(DatStatUtil datstatUtil, SurveyInstance surveyInstance) {
        SurveySession surveySession = surveyInstance.getSurveySession();
        HttpResponse postResponse = datstatUtil.sendRequest(DatStatUtil.MethodType.POST, surveySession.getUri(), new SurveySessionRequest(surveyInstance));
        if (postResponse.getStatusCode() != 200) {
            logger.error("Survey instance save failed with " + postResponse.getStatusMessage());
            throw new RuntimeException("Save returned " + postResponse.getStatusCode() + " with message " + postResponse.getStatusMessage());
        }
    }

    /**
     * Terminates a survey.
     */
    public <T extends SurveyInstance> void terminateSurveyInstance(DatStatUtil datstatUtil,
                                                                   Class<T> surveyInstanceClass,
                                                                   String surveySessionId) {
        logger.info("About to terminate survey session = " + surveySessionId);
        try {
            SurveyInstance surveyInstance = fetchSurveyInstance(datstatUtil, surveyInstanceClass, surveySessionId);
            if (!surveyInstance.getSubmissionStatus().equals(SurveyInstance.SubmissionStatus.TERMINATED)) {
                surveyInstance.setSubmissionStatus(SurveyInstance.SubmissionStatus.TERMINATED);
                saveSurveyInstance(datstatUtil, surveyInstance);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("An error occurred terminating survey instance ", e);
        }
    }

    /**
     * Class that wraps up survey instance data to help
     * with the creation of a new survey instance
     */
    private static class SurveySessionRequest<T extends SurveyInstance> {

        @Key("Data")
        @SerializedName("Data")
        private Collection<T> sessionData = new ArrayList<>();

        public SurveySessionRequest() {}

        public SurveySessionRequest(T surveyInstance) {
            this.sessionData.add(surveyInstance);
        }

        public Object getSurveyInstance() {
            if (sessionData.size() != 1) {
                throw new RuntimeException("We have " + sessionData.size() + " survey sessions");
            }
            else {
                return sessionData.iterator().next();
            }
        }
    }
}
