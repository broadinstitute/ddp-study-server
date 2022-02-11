package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.MedicalRecordLog;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class MedicalRecordLogRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordLogRoute.class);

    private static final String SQL_SELECT_MR_LOG = "SELECT medical_record_log_id, date, comments, type FROM ddp_medical_record_log WHERE medical_record_id = ?";
    private static final String SQL_UPDATE_MR_LOG = "UPDATE ddp_medical_record_log SET date = ?, comments = ?, last_changed = ? WHERE medical_record_log_id = ?";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (UserUtil.checkUserAccess(null, userId, "mr_view", null)) {
            String medicalRecordId = request.params(RequestParameter.MEDICALRECORDID);
            if (StringUtils.isNotBlank(medicalRecordId)) {
                if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())) {
                    return getMedicalRecordLogs(medicalRecordId);
                }
                if (request.requestMethod().equals(RoutePath.RequestMethod.PATCH.toString())) {
                    String requestBody = request.body();
                    MedicalRecordLog medicalRecordLog = new Gson().fromJson(requestBody, MedicalRecordLog.class);
                    try {
                        saveMedicalRecordLog(medicalRecordId, medicalRecordLog);
                        return new Result(200);
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Failed to save medical record log ", e);
                    }
                }
                throw new RuntimeException("Request method was not mapped " + request.requestMethod());
            }
            else {
                throw new RuntimeException("Medical record id was missing");
            }
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    /**
     * Read logs form ddp_medical_record_log table
     *
     * @return List<MedicalRecordLog>
     * @throws Exception
     */
    public Collection<MedicalRecordLog> getMedicalRecordLogs(@NonNull String medicalRecordId) {
        List<MedicalRecordLog> logs = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MR_LOG)) {
                stmt.setString(1, medicalRecordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        logs.add(new MedicalRecordLog(
                                rs.getString(DBConstants.MEDICAL_RECORD_LOG_ID),
                                rs.getString(DBConstants.DATE),
                                rs.getString(DBConstants.COMMENTS),
                                rs.getString(DBConstants.TYPE)
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of logs for medical record" + medicalRecordId, results.resultException);
        }
        logger.info("Found " + logs.size() + " logs ");
        return logs;
    }

    /**
     * Save value at ddp_medical_record_log table
     * for the given medicalRecordLog
     *
     * @return boolean value if changes where successful
     * @throws Exception
     */
    public void saveMedicalRecordLog(@NonNull String medicalRecordLogId, @NonNull MedicalRecordLog medicalRecordLog) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MR_LOG)) {
                stmt.setString(1, medicalRecordLog.getDate());
                stmt.setString(2, medicalRecordLog.getComments());
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, medicalRecordLogId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated medical record log w/ id " + medicalRecordLogId);
                }
                else {
                    throw new RuntimeException("Error updating medical record log w/ id " + medicalRecordLogId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error saving medicalRecordLog w/ id" + medicalRecordLogId, results.resultException);
        }
    }
}
