package org.broadinstitute.dsm.route;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class AssignParticipantRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(AssignParticipantRoute.class);

    public static final String PARTICIPANT_LINK = "/permalink/participant?realm=%1";

    public static final String EMAIL_TYPE = "PARTICIPANT_ASSIGNED";
    public static final String EMAIL_TYPE_REMINDER = "PARTICIPANT_REMINDER";

    public static final String ASSIGN_MR = "assignMR";
    public static final String ASSIGN_TISSUE = "assignTissue";

    private static final String SQL_UPDATE_MR_ASSIGNEE = "UPDATE ddp_participant SET assignee_id_mr = ?, last_changed = ? WHERE participant_id = ?";
    private static final String SQL_UPDATE_TISSUE_ASSIGNEE = "UPDATE ddp_participant SET assignee_id_tissue = ?, last_changed = ? WHERE participant_id = ?";

    private final String queryForDDPParticipantId;

    private String frontendUrl;

    private NotificationUtil notificationUtil;

    public AssignParticipantRoute(@NonNull String queryForDDPParticipantId, @NonNull String frontendUrl, @NonNull NotificationUtil notificationUtil) {
        this.queryForDDPParticipantId = queryForDDPParticipantId;
        this.frontendUrl = frontendUrl;
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = RoutePath.getRealm(request);
        if (UserUtil.checkUserAccess(realm, userId, "mr_request", null)) {
            String requestBody = request.body();
            JsonArray scans = (JsonArray) (new JsonParser().parse(requestBody));
            if (scans.size() > 0) {
                QueryParamsMap queryParams = request.queryMap();
                boolean assignMr = false;
                if (queryParams.value(ASSIGN_MR) != null) {
                    assignMr = queryParams.get(ASSIGN_MR).booleanValue();
                }
                boolean assignTissue = false;
                if (queryParams.value(ASSIGN_TISSUE) != null) {
                    assignTissue = queryParams.get(ASSIGN_TISSUE).booleanValue();
                }
                if (assignParticipants(scans, realm, assignMr, assignTissue)) {
                    return new Result(500, "Failed to assign one or more participants");
                }
            }
            return new Result(200);
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public boolean assignParticipants(@NonNull JsonArray payload, @NonNull String realm, boolean assignMr, boolean assignTissue) {
        HashSet<String> shortIds = new HashSet<>();
        String email = null;
        boolean hasError = false;
        String participantId = null;
        String assigneeId = null;
        String shortId = null;

        for (JsonElement jsonElement : payload) {
            //there will only be 1 assignee per request
            if (StringUtils.isBlank(email)) {
                email = jsonElement.getAsJsonObject().get("email").getAsString();
            }
            //there will only be 1 assignee per request
            if (StringUtils.isBlank(assigneeId)) {
                assigneeId = jsonElement.getAsJsonObject().get("assigneeId").getAsString();
            }
            if (jsonElement.getAsJsonObject().get("participantId") != null) {
                participantId = jsonElement.getAsJsonObject().get("participantId").getAsString();
            }
            if (jsonElement.getAsJsonObject().get("shortId") != null) {
                shortId = jsonElement.getAsJsonObject().get("shortId").getAsString();
            }

            if ("-1".equals(assigneeId)) {
                //remove mr assignee
                if (assignMr) {
                    if (!assign(null, participantId, SQL_UPDATE_MR_ASSIGNEE)) {
                        hasError = true;
                    }
                }
                //remove tissue assignee
                if (assignTissue) {
                    if (!assign(null, participantId, SQL_UPDATE_TISSUE_ASSIGNEE)) {
                        hasError = true;
                    }
                }
                //remove all emails for participant
            }
            else {
                //assign participant
                if (assignMr) {
                    if (!assignParticipant(shortIds, assigneeId, participantId, shortId, email, realm, SQL_UPDATE_MR_ASSIGNEE)) {
                        hasError = true;
                    }
                }
                if (assignTissue) {
                    if (!assignParticipant(shortIds, assigneeId, participantId, shortId, email, realm, SQL_UPDATE_TISSUE_ASSIGNEE)) {
                        hasError = true;
                    }
                }
            }
        }
        if (!shortIds.isEmpty()) {
            String message = "";
            for (String sId : shortIds) {
                message += sId + ", ";
            }
            message = message.replaceAll(", $", "");
            doNotification(email, message, email, EMAIL_TYPE, false, realm);
        }
        return hasError;
    }

    public boolean assign(String assigneeId, String participantId, String query) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, assigneeId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, participantId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating participant " + participantId + " it was updating " + result + " rows");
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Failed to update assignee for participant " + participantId + results.resultException);
            return false;
        }
        return true;
    }

    public boolean assignParticipant(HashSet<String> shortIds, String assigneeId, String participantId, String shortId,
                                     String email, String realm, String query) {
        if (assign(assigneeId, participantId, query)) {
            logger.info("Updated assignee for participant " + participantId);
            if (shortId != null) {
                shortIds.add(shortId);
            }
            else {
                shortIds.add(getDDPParticipantId(participantId));
            }
            if (StringUtils.isNotBlank(email) && StringUtils.isNotBlank(assigneeId)) {
                doNotification(email, shortId, participantId, EMAIL_TYPE_REMINDER, true, realm);
            }
            return true;
        }
        return false;
    }

    public void doNotification(String email, String message, String recordId, String reason, boolean onlyFuture, String realm) {
        Map<String, String> mapy = new HashMap<>();
        mapy.put(":customText", message);
        Recipient emailRecipient = new Recipient(email);
        emailRecipient.setUrl(frontendUrl + PARTICIPANT_LINK.replace("%1", realm));
        emailRecipient.setCurrentStatus(reason);
        emailRecipient.setSurveyLinks(mapy);
        if (onlyFuture) {
            notificationUtil.removeOldNotifications("", recordId);
            notificationUtil.queueFutureEmails(reason, emailRecipient, recordId);
        }
        else {
            notificationUtil.queueCurrentAndFutureEmails(reason, emailRecipient, recordId);
        }
    }


    public String getDDPParticipantId(@NonNull String participantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(queryForDDPParticipantId,
                    ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count == 1 && rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                    }
                    else {
                        throw new RuntimeException("Error getting ddpParticipantId (count: " + count + ")");
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get ddpParticipantId ", results.resultException);
        }
        return (String) results.resultValue;
    }
}
