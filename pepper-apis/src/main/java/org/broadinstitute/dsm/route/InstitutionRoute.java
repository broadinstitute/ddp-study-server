package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.UserUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class InstitutionRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InstitutionRoute.class);

    public static final String APPLY_DESTRUCTION_POLICY = "UPDATE ddp_onc_history_detail SET destruction_policy = ?, last_changed = ?, changed_by = ? " +
            "WHERE onc_history_detail_id <> 0 AND onc_history_detail_id in " +
            "(SELECT onc_history_detail_id FROM (SELECT * from ddp_onc_history_detail) as something " +
            "WHERE something.facility = ?)";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        JSONObject jsonObject = new JSONObject(requestBody);
        String user = String.valueOf(jsonObject.get(RequestParameter.USER_ID));
        if (!userId.equals(user)) {
            throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + user);
        }
        if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
            if (StringUtils.isNotBlank(requestBody)) {
                String ddpParticipantId = (String) jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID);
                String realm = (String) jsonObject.get(RequestParameter.DDP_REALM);
                if (UserUtil.checkUserAccess(realm, userId, "mr_view")) {
                    if (StringUtils.isNotBlank(ddpParticipantId) && StringUtils.isNotBlank(realm)) {
                        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByDDPParticipantAndRealm(realm, ddpParticipantId, DBConstants.HAS_MEDICAL_RECORD_INFORMATION_IN_DB);
                        if (ddpInstance != null) {
                            if (!ddpInstance.isHasRole()) { // if ddp does not have info in db (all others except mbc)
                                return MedicalRecord.getDDPInstitutionInfo(ddpInstance, ddpParticipantId);
                            }
                            else { //mbc (get institution information from db)
                                return MedicalRecord.getInstitutionInfoFromDB(realm, ddpParticipantId);
                            }
                        }
                    }
                    logger.warn("Error missing ddpParticipantId " + ddpParticipantId + " or realm " + realm + " w/ userId " + user);
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        else if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            String policy = "";
            if (jsonObject.has(RequestParameter.POLICY)) {
                policy = String.valueOf(jsonObject.get(RequestParameter.POLICY));
            }
            if (UserUtil.checkUserAccess(null, userId, "mr_request")) {
                String facility = String.valueOf(jsonObject.get(RequestParameter.FACILITY));
                String userMail = String.valueOf(jsonObject.get(RequestParameter.USER_MAIL));
                applyDestructionPolicy(userMail, facility, policy);
                return new Result(200);
            }
            else {
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
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't update the destruction policy for " + facility, results.resultException);
        }
    }

}
