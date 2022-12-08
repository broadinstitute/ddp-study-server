package org.broadinstitute.dsm.route;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class InstitutionRoute extends RequestHandler {

    public static final String APPLY_DESTRUCTION_POLICY =
            "UPDATE ddp_onc_history_detail SET destruction_policy = ?, last_changed = ?, changed_by = ? "
                    + "WHERE onc_history_detail_id <> 0 AND onc_history_detail_id in "
                    + "(SELECT onc_history_detail_id FROM (SELECT * from ddp_onc_history_detail) as something "
                    + "WHERE something.facility = ?)";
    private static final Logger logger = LoggerFactory.getLogger(InstitutionRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
        String user = String.valueOf(jsonObject.get(RequestParameter.USER_ID));
        if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
            if (StringUtils.isNotBlank(requestBody)) {
                String ddpParticipantId = jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID).getAsString();
                String realm = jsonObject.get(RequestParameter.DDP_REALM).getAsString();
                if (UserUtil.checkUserAccess(realm, userId, "mr_view", user)) {
                    if (StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(realm)) {
                        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
                        if (ddpInstance != null) {
                            return MedicalRecord.getDDPInstitutionInfo(ddpInstance, ddpParticipantId);
                        }
                    }
                    logger.warn("Error missing ddpParticipantId " + ddpParticipantId + " or realm " + realm + " w/ userId " + user);
                } else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
        } else if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            String policy = "";
            if (jsonObject.has(RequestParameter.POLICY)) {
                policy = String.valueOf(jsonObject.get(RequestParameter.POLICY));
            }
            if (UserUtil.checkUserAccess(null, userId, "mr_request", user)) {
                String facility = String.valueOf(jsonObject.get(RequestParameter.FACILITY));
                String userMail = String.valueOf(jsonObject.get(RequestParameter.USER_MAIL));
                applyDestructionPolicy(userMail, facility, policy);
                return new Result(200);
            } else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        logger.error("Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }

    private void applyDestructionPolicy(@NonNull String user, String facility, String policy) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(APPLY_DESTRUCTION_POLICY)) {
                if (StringUtils.isNotBlank(user) && StringUtils.isNotBlank(facility) && StringUtils.isNotBlank(policy)) {
                    stmt.setString(1, policy);
                    stmt.setString(2, String.valueOf(System.currentTimeMillis()));
                    stmt.setString(3, user);
                    stmt.setString(4, facility);
                    stmt.executeUpdate();
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't update the destruction policy for " + facility, results.resultException);
        }
    }

}
