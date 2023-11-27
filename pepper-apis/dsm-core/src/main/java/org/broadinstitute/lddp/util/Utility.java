package org.broadinstitute.lddp.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import lombok.NonNull;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.security.SecurityHelper;

/**
 * Contains a collection of useful static methods.
 */
public class Utility {

    public static String getTokenFromHeader(@NonNull spark.Request req) {
        String tokenHeader = req.headers(SecurityHelper.AUTHORIZATION_HEADER);
        if (tokenHeader == null) {
            return "";
        }

        //handle basic or bearer
        if (tokenHeader.contains(SecurityHelper.BEARER)) {
            return tokenHeader.replaceFirst(SecurityHelper.BEARER, "");
        } else if (tokenHeader.contains(SecurityHelper.BASIC)) {
            try {
                String b64Credentials = tokenHeader.replaceFirst(SecurityHelper.BASIC, "");
                String credentials = new String(Base64.getDecoder().decode(b64Credentials));
                return credentials.split(":")[1];
            } catch (Exception ex) {
                return "";
            }
        } else {
            return "";
        }
    }

    public static long getCurrentEpoch() {
        return System.currentTimeMillis() / 1000;
    }

    public static void updateProcessedInQueue(@NonNull Long queueId, @NonNull String updateSql, @NonNull String errorMsg,
                                              QueueStatus queueStatus) {
        ArrayList<Long> queueIds = new ArrayList<>();
        queueIds.add(queueId);
        updateProcessedInQueue(queueIds, updateSql, errorMsg, queueStatus);
    }

    private static void updateProcessedInQueue(@NonNull ArrayList<Long> queueIds, @NonNull String updateSql, @NonNull String errorMsg,
                                               QueueStatus queueStatus) {
        String[] questions = new String[queueIds.size()];
        Arrays.fill(questions, "?");
        String questionsForIn = String.join(",", questions);

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(updateSql.replace("X", questionsForIn))) {
                long processedEpoch = Utility.getCurrentEpoch();

                if (QueueStatus.STARTED == queueStatus) {
                    processedEpoch = -1;
                }

                if (QueueStatus.UNPROCESSED != queueStatus) {
                    stmt.setLong(1, processedEpoch);
                } else {
                    stmt.setNull(1, Types.BIGINT);
                }

                int index = 1;
                for (Long id : queueIds) {
                    stmt.setLong(++index, id);
                }

                dbVals.resultValue = stmt.executeUpdate();
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(errorMsg, results.resultException);
        }
    }

    public static String getHttpResponseAsString(HttpResponse response) throws Exception {
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }

    public enum QueueStatus {
        UNPROCESSED, STARTED, PROCESSED
    }
}
