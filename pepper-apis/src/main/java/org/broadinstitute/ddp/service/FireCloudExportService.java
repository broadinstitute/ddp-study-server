package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.constants.FireCloudConstants.MAX_FC_TRIES;
import static org.broadinstitute.ddp.constants.FireCloudConstants.SEC_BETWEEN_FC_TRIES;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.ConsentElectionDao;
import org.broadinstitute.ddp.db.StudyActivityDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.FireCloudException;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.json.export.ExportStudyPayload;
import org.broadinstitute.ddp.json.export.FireCloudEntities;
import org.broadinstitute.ddp.json.export.FireCloudEntity;
import org.broadinstitute.ddp.json.export.Workspace;
import org.broadinstitute.ddp.json.export.WorkspaceMetadata;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FireCloudExportService {
    // firecloud will only allow alphanumeric, underscore, and dash.  No periods.

    private static final Logger LOG = LoggerFactory.getLogger(FireCloudExportService.class);
    private static final String[] FIRECLOUD_SCOPES = new String[] {"https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"};
    private static final String PARTICIPANT_ENTITY_TYPE = "participant";
    private static String baseUrl;
    private final String firecloudExportQuery;
    private Gson gson = new Gson();
    private ConsentService consentService;

    /**
     * Instantiate FireCloudExportService object.
     */
    public FireCloudExportService(String firecloudExportQuery, String baseUrl) {
        this.firecloudExportQuery = firecloudExportQuery;
        this.baseUrl = baseUrl;

        StudyActivityDao studyActivityDao = new StudyActivityDao();
        ConsentElectionDao consentElectionDao = new ConsentElectionDao();
        consentService = new ConsentService(new TreeWalkInterpreter(), studyActivityDao, consentElectionDao);
    }

    public static FireCloudExportService fromSqlConfig(Config sqlConfig, String baseUrl) {
        return new FireCloudExportService(sqlConfig.getString(SqlConstants.FireCloud.FIRECLOUD_STUDY_QUERY), baseUrl);
    }

    public static FireCloudExportService fromSqlConfig(Config sqlConfig) {
        return new FireCloudExportService(sqlConfig.getString(SqlConstants.FireCloud.FIRECLOUD_STUDY_QUERY),
                RouteConstants.FireCloud.fireCloudBaseUrl);
    }

    /**
     * Get Google credential, convert to token, make into authorization header to access FC things.
     *
     * @param pathToFireCloudServiceAccount file path to FC Service Account credentials
     * @return header that allows access to FC
     */
    static Header newFcAuthHeader(String pathToFireCloudServiceAccount) {
        GoogleCredential credential;
        try {
            credential = GoogleCredential.fromStream(new FileInputStream(pathToFireCloudServiceAccount))
                    .createScoped(Arrays.asList(FIRECLOUD_SCOPES));
            credential.refreshToken();
        } catch (IOException e) {
            throw new DDPException("Could not setup credentials from " + pathToFireCloudServiceAccount, e);
        }
        return new BasicHeader("Authorization", "Bearer " + credential.getAccessToken());
    }


    /**
     * Given a workspace, workspace namespace, and an includeAfterDate provided by exportStudyPayload,
     * export a given study to FireCloud.
     *
     * @param handle                        the database connection
     * @param exportStudyPayload            object containing workspace, workspace namespace, and includeAfterDate
     * @param studyGuid                     guid of study to be exported
     * @param pathToFireCloudServiceAccount path to FC permissions in pepper
     * @return list of the id's of participants in study (used to clean workspace after testing)
     */
    public Collection<String> exportStudy(Handle handle, ExportStudyPayload exportStudyPayload, String studyGuid,
                                          String pathToFireCloudServiceAccount)
            throws IOException {
        String url = baseUrl + "/" + exportStudyPayload.getWorkspaceNamespace() + "/"
                + exportStudyPayload.getWorkspaceName() + "/importEntities";

        FireCloudEntities fireCloudEntities
                = getEntitiesForStudyByUmbrellaStudyGuid(handle, studyGuid, exportStudyPayload.getIncludeAfterDate());
        String tsv = fireCloudEntities.toTSV();

        Request request = Request.Post(url)
                .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount))
                .bodyForm(new BasicNameValuePair("entities", tsv));
        String waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES
                + " seconds then trying to post study to workspace again.";
        HttpResponse httpResponse = runFireCloudAPICallWithBody(request, waitingForFCMessage);
        int status = httpResponse.getStatusLine().getStatusCode();

        Collection<String> participantIds = new ArrayList<>();

        if (status >= HttpStatus.SC_BAD_REQUEST) {
            throw new FireCloudException("Warning - was unable to post study to FireCloud.");
        } else {
            String responseBody = EntityUtils.toString(httpResponse.getEntity());
            if (status != HttpStatus.SC_OK) {
                LOG.warn("Firecloud responded with:" + responseBody);
            }
            for (FireCloudEntity fireCloudEntity : fireCloudEntities.getFireCloudEntities()) {
                participantIds.add(fireCloudEntity.getEntityName());
            }
        }
        return participantIds;
    }

    /**
     * Given a study, the workspace parameters, and the includeAfterDate,
     * see if the information on the study in the database matches what's on FireCloud.
     *
     * @param handle                        the database connection
     * @param studyGuid                     guid of study that was exported
     * @param exportStudyPayload            object containing workspace, workspace namespace, and includeAfterDate
     * @param pathToFireCloudServiceAccount path to FC permissions in pepper
     * @return boolean indicating whether the database matches FireCloud for the study
     */
    public boolean compareFireCloudToStudyData(Handle handle, String studyGuid, ExportStudyPayload exportStudyPayload,
                                               String pathToFireCloudServiceAccount)
            throws IOException {
        boolean success = true;

        Date includeOnlyAfter = exportStudyPayload.getIncludeAfterDate();
        FireCloudEntities fireCloudEntities
                = getEntitiesForStudyByUmbrellaStudyGuid(handle, studyGuid, includeOnlyAfter);

        for (FireCloudEntity fireCloudEntity : fireCloudEntities.getFireCloudEntities()) {
            String url = baseUrl + "/" + exportStudyPayload.getWorkspaceNamespace()
                    + "/" + exportStudyPayload.getWorkspaceName() + "/entities/"
                    + PARTICIPANT_ENTITY_TYPE + "/" + fireCloudEntity.getEntityName();

            Request request = Request.Get(url)
                    .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount));
            String waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES
                    + " seconds then trying to retrieve workspace again.";
            HttpResponse httpResponse = runFireCloudAPICallWithBody(request, waitingForFCMessage);
            int status = httpResponse.getStatusLine().getStatusCode();
            if (status >= HttpStatus.SC_BAD_REQUEST) {
                LOG.error("Warning - unable to retrieve workspace to verify successful export.");
            } else {
                Type type = new TypeToken<JsonObject>() {
                }.getType();
                String responseEntity = EntityUtils.toString(httpResponse.getEntity());
                JsonObject json = gson.fromJson(responseEntity, type);
                if (json.has("name")) {
                    FireCloudEntity firecloudEntity = new FireCloudEntity(responseEntity);

                    for (String key : fireCloudEntity.getAttributes().keySet()) {
                        if (firecloudEntity.getAttributes().containsKey(key)) {
                            String fcValue = firecloudEntity.getAttributes().get(key);
                            String postedValue = fireCloudEntity.getAttributes().get(key);
                            if (!fcValue.equals(postedValue)) {
                                success = false;
                                break;
                            }
                        }
                    }
                } else {
                    throw new FireCloudException("Could not find instance of FireCloud entity: "
                            + fireCloudEntity.getEntityName()
                            + ". Error messages said: " + json.get("message"));
                }
            }
        }
        return success;
    }


    /**
     * Given firecloud service account set up by token, get all workspaces that account has writer/ owner access.
     *
     * @return list of workspaces that have writer/owner access too
     */
    public List<Workspace> getWorkspaces(String pathToFireCloudServiceAccount)
            throws IOException {
        List<Workspace> workspaceNames = new ArrayList<>();

        Request request = Request.Get(baseUrl)
                .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount));
        String waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES + " seconds then "
                + "trying to recover workspaces information again.";
        HttpResponse httpResponse = runFireCloudAPICallWithBody(request, waitingForFCMessage);
        int status = httpResponse.getStatusLine().getStatusCode();
        if (status >= HttpStatus.SC_BAD_REQUEST) {
            throw new FireCloudException("Warning - was unable to query workspaces from FireCloud.");
        } else {
            String response = EntityUtils.toString(httpResponse.getEntity());
            Type listType = new TypeToken<ArrayList<WorkspaceMetadata>>() {
            }.getType();
            ArrayList<WorkspaceMetadata> output = gson.fromJson(response, listType);

            for (WorkspaceMetadata anOutput : output) {
                if (WorkspaceMetadata.hasWriteAccess(anOutput.getAccessLevel())) {
                    workspaceNames.add(anOutput.getWorkspace());
                }
            }
        }
        return workspaceNames;
    }

    /**
     * Given a guid of a study and language, query each activity each user has done. Then by each user, get their guid
     * and map their answers to the corresponding questions.
     *
     * @param handle           the database connection
     * @param studyGuid        guid  of given study
     * @param includeOnlyAfter date after which to include data.  Any
     *                         activity data collected prior to this date will be ignored.
     *                         If null, all data will be included
     * @return an object containing all FC entities
     */
    final FireCloudEntities getEntitiesForStudyByUmbrellaStudyGuid(Handle handle, String studyGuid,
                                                                   Date includeOnlyAfter) {
        Collection<String> header = new TreeSet<>();
        Map<String, FireCloudEntity> fcMetadataForParticipant = new HashMap<>();
        Date includeAfter = new Date(0);
        if (includeOnlyAfter != null) {
            includeAfter = includeOnlyAfter;
        }

        Collection questionsToIgnore = Arrays.asList("D813B49E6B_TOGGLED_Q",
                "9028507D7_CONTROL_Q", "195FC87948_CONTROL_Q");

        LOG.info("Querying export data for study {} after {}", studyGuid, includeAfter);
        long startTime = System.currentTimeMillis();
        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(firecloudExportQuery)) {
                stmt.setString(1, studyGuid);
                stmt.setLong(2, Instant.ofEpochMilli(includeAfter.getTime()).getEpochSecond());

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String attributeValue = null;
                    String userGuid = rs.getString(2);
                    String questionStableId = rs.getString(4);
                    String questionType = rs.getString(5);
                    header.add(questionStableId);

                    Boolean booleanAnswer = rs.getBoolean(8);
                    String textAnswer = rs.getString(7);
                    String picklistAnswer = rs.getString(9);

                    int monthAnswer = rs.getInt(11);
                    int dayAnswer = rs.getInt(12);
                    int yearAnswer = rs.getInt(13);
                    String dateAnswer = (new DateValue(yearAnswer, monthAnswer, dayAnswer)).toExportString();

                    if (!fcMetadataForParticipant.containsKey(userGuid)) {
                        fcMetadataForParticipant.put(userGuid, new FireCloudEntity(userGuid, PARTICIPANT_ENTITY_TYPE));
                    }

                    FireCloudEntity fireCloudEntity = fcMetadataForParticipant.get(userGuid);
                    if (QuestionType.BOOLEAN.name().equals(questionType)) {
                        attributeValue = booleanAnswer.toString();
                    } else if (QuestionType.TEXT.name().equals(questionType)) {
                        attributeValue = textAnswer;
                    } else if (QuestionType.PICKLIST.name().equals(questionType)) {
                        attributeValue = picklistAnswer;
                    } else if (QuestionType.DATE.name().equals(questionType)) {
                        attributeValue = dateAnswer;
                    }
                    if (!questionsToIgnore.contains(questionStableId)) {
                        fireCloudEntity.addAttribute(questionStableId, attributeValue);
                    }
                }
                for (String userGuid : fcMetadataForParticipant.keySet()) {
                    List<ConsentSummary> consentSummaryList
                            = consentService.getAllConsentSummariesByUserGuid(handle, userGuid, studyGuid);
                    FireCloudEntity fireCloudEntity = fcMetadataForParticipant.get(userGuid);

                    if (consentSummaryList != null) {
                        for (ConsentSummary consentSummary : consentSummaryList) {
                            List<ConsentElection> consentElectionList = consentSummary.getElections();

                            if (consentElectionList != null) {
                                for (ConsentElection consentElection : consentElectionList) {
                                    Boolean selected = consentElection.getSelected();
                                    String message = (selected != null) ? selected.toString() : "n/a";
                                    header.add(consentElection.getStableId());
                                    fireCloudEntity.addAttribute(consentElection.getStableId(), message);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DDPException("Error querying firecloud export data for study " + studyGuid, e);
        }

        LOG.info("Retrieved FC export data for {} participants in {}ms",
                fcMetadataForParticipant.size(), (System.currentTimeMillis() - startTime));

        return new FireCloudEntities(fcMetadataForParticipant.values(), header);
    }

    /**
     * Given an array of participant_id's, remove these entities from given FireCloud workspace. Return HTTP code.
     *
     * @param participantIds array of participant_id's (the primary attribute of entities)
     * @param entityType     the type of entity a given row is
     */
    public int deleteGivenEntities(String[] participantIds, String entityType, String workspaceNamespace,
                                   String workspaceName, String pathToFireCloudServiceAccount)
            throws IOException {
        String deleteEntitiesUrl = baseUrl + "/" + workspaceNamespace + "/" + workspaceName + "/entities/delete";

        FireCloudEntity[] fireCloudEntities = new FireCloudEntity[participantIds.length];
        for (int i = 0; i < participantIds.length; i++) {
            fireCloudEntities[i] = new FireCloudEntity(participantIds[i], new HashMap<>(), entityType);
        }
        LOG.info("Deleting {} firecloud entities", fireCloudEntities.length);
        String json = gson.toJson(fireCloudEntities);

        Request request = Request.Post(deleteEntitiesUrl)
                .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount))
                .bodyString(json, ContentType.APPLICATION_JSON);
        String waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES
                + " seconds then trying to delete entities from workspace again.";
        int status = runFireCloudAPICall(request, waitingForFCMessage);
        if (status == HttpStatus.SC_BAD_GATEWAY) {
            LOG.error("Warning - was unable to delete entities from FireCloud.");
        }
        return status;
    }

    /**
     * Delete all entities in given FireCloud workspace.
     */
    public int deleteAllEntitiesInWorkspace(String entityType, String workspaceNamespace, String workspaceName,
                                            String pathToFireCloudServiceAccount)
            throws IOException, InterruptedException {
        String getEntityBaseUrl = baseUrl + "/" + workspaceNamespace + "/" + workspaceName + "/entities/" + entityType;

        Request request = Request.Get(getEntityBaseUrl)
                .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount));
        String waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES
                + " seconds then trying to retrieve" + " workspace again.";
        HttpResponse getResponse = runFireCloudAPICallWithBody(request, waitingForFCMessage);
        int status = getResponse.getStatusLine().getStatusCode();

        if (status >= HttpStatus.SC_BAD_REQUEST) {
            LOG.error("Warning - was unable to retrieve FC workspace so could not delete all entities in workspace.");
            status = HttpStatus.SC_BAD_GATEWAY;
        } else {
            Type listType = new TypeToken<ArrayList<FireCloudEntity>>() {
            }.getType();
            ArrayList<FireCloudEntity> output
                    = gson.fromJson(EntityUtils.toString(getResponse.getEntity()), listType);

            FireCloudEntity[] entities = new FireCloudEntity[output.size()];
            entities = output.toArray(entities);
            for (FireCloudEntity entity : entities) {
                entity.setEntityNameFromNameFromGetParticipantsEndpoint();
            }
            String json = gson.toJson(entities);

            String deleteEntitiesUrl = baseUrl + "/" + workspaceNamespace
                    + "/" + workspaceName + "/entities/delete";

            request = Request.Post(deleteEntitiesUrl)
                    .addHeader(newFcAuthHeader(pathToFireCloudServiceAccount))
                    .bodyString(json, ContentType.APPLICATION_JSON);
            waitingForFCMessage = "FireCloud down. Waiting " + SEC_BETWEEN_FC_TRIES + " seconds then trying to delete "
                    + "whole workspace again.";
            status = runFireCloudAPICall(request, waitingForFCMessage);
            if (status == HttpStatus.SC_BAD_GATEWAY) {
                LOG.error("Warning - was unable to delete whole workspace.");
            }
        }
        return status;
    }

    /**
     * Runs API call to FireCloud based on given request. If request fails, waits SEC_BETWEEN_FC_TRIES seconds
     * then tries again. This repeats for MAX_FC_TRIES.
     *
     * @param request             API request to be executed
     * @param waitingForFCMessage error message indicating trying FC again soon
     * @return whatever success HTTP code FC gives if successful call,
     *      otherwise HttpStatus.SC_BAD_GATEWAY indicating bad gateway
     */
    int runFireCloudAPICall(Request request, String waitingForFCMessage)
            throws IOException {
        int status = HttpStatus.SC_BAD_REQUEST;
        int tries = 0;
        while (tries < MAX_FC_TRIES) {
            Response response = request.execute();
            status = response.returnResponse().getStatusLine().getStatusCode();

            if (status < HttpStatus.SC_BAD_REQUEST) {
                break;
            } else {
                tries++;
                if (tries < MAX_FC_TRIES) {
                    LOG.warn(waitingForFCMessage);
                    try {
                        TimeUnit.SECONDS.sleep(SEC_BETWEEN_FC_TRIES);
                    } catch (InterruptedException e) {
                        LOG.warn("Error waiting for FC API call, was interrupted.", e);
                    }

                } else {
                    status = HttpStatus.SC_BAD_GATEWAY;
                }
            }
        }
        return status;
    }

    /**
     * Runs API call to FireCloud based on given GET request. If request fails, waits SEC_BETWEEN_FC_TRIES seconds
     * then tries again. This repeats for MAX_FC_TRIES.
     *
     * @param request            API request to be executed
     * @param nextAttemptMessage error message indicating trying FC again soon
     * @return HttpResponse from FireCloud API
     */
    HttpResponse runFireCloudAPICallWithBody(Request request, String nextAttemptMessage)
            throws IOException {
        int status;
        int tries = 0;
        HttpResponse httpResponse = null;
        while (tries < MAX_FC_TRIES) {
            Response response = request.execute();
            httpResponse = response.returnResponse();
            status = httpResponse.getStatusLine().getStatusCode();

            if (status < HttpStatus.SC_BAD_REQUEST) {
                break;
            } else {
                tries++;
                if (tries < MAX_FC_TRIES) {
                    LOG.warn(nextAttemptMessage);
                    try {
                        TimeUnit.SECONDS.sleep(SEC_BETWEEN_FC_TRIES);
                    } catch (InterruptedException e) {
                        LOG.warn("Error waiting for FC API call, was interrupted.", e);
                    }
                }
            }
        }
        return httpResponse;
    }
}
