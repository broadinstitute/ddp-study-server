package org.broadinstitute.lddp.util;

import lombok.NonNull;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Contains a collection of useful static methods.
 */
public class Utility
{
    private static final Logger logger = LoggerFactory.getLogger(Utility.class);

    private static final String LOG_PREFIX = "UTILITY - ";
    public static final String SQL_DB_EXISTS = "SELECT 1";

    public enum QueueStatus
    {
        UNPROCESSED, STARTED, PROCESSED
    }

    public static final String FILE_ARG_MISSING_MESSAGE = "No configuration file specified.";
    public static final String ENV_LOAD_ERROR_MESSAGE = "Problem loading ENV: ";
    public static final String CONFIG_INCOMPLETE_ERROR_MESSAGE = "Required config setting is missing: ";
    public static final String CONFIG_AUTH_INCOMPLETE_ERROR_MESSAGE = "Required Auth0 config setting is missing: ";
    public static final String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'UTC'";
    public static final String EDC_DATE_FORMAT = "MM-dd-yyyy";

    public static String getTokenFromHeader(@NonNull spark.Request req) {
        String tokenHeader = req.headers(SecurityHelper.AUTHORIZATION_HEADER);
        if (tokenHeader == null) return "";

        //handle basic or bearer
        if (tokenHeader.contains(SecurityHelper.BEARER)) {
            return tokenHeader.replaceFirst(SecurityHelper.BEARER, "");
        }
        else if (tokenHeader.contains(SecurityHelper.BASIC)) {
            try {
                String b64Credentials = tokenHeader.replaceFirst(SecurityHelper.BASIC, "");
                String credentials = new String(Base64.getDecoder().decode(b64Credentials));
                return credentials.split(":")[1];
            }
            catch (Exception ex) {
                return "";
            }
        }
        else return "";
    }

    /**
     * Handy method for just getting a count of records in a DDP table.
     * @param sql
     * @return count
     * @
     */
    public static int getCountInTable(@NonNull String sql)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(sql))
            {
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (rs.next())
                    {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null)
        {
            throw new DMLException("An error occurred while trying to get a record count.", results.resultException);
        }

        return (Integer)results.resultValue;
    }

    public static boolean dbCheck()
    {
        boolean ok = false;

        try
        {
            ok = (getCountInTable(SQL_DB_EXISTS) == 1);
        }
        catch (Exception ex)
        {
            logger.error(LOG_PREFIX + "A problem occurred querying the DB.", ex);
        }

        return ok;
    }

    public static long getCurrentEpoch()
    {
        return System.currentTimeMillis()/1000;
    }

    public static void updateProcessedInQueue(@NonNull Long queueId, @NonNull String updateSql, @NonNull String errorMsg, QueueStatus queueStatus) {
        ArrayList<Long> queueIds = new ArrayList<>();
        queueIds.add(queueId);
        updateProcessedInQueue(queueIds, updateSql, errorMsg, queueStatus);
    }

    private static void updateProcessedInQueue(@NonNull ArrayList<Long> queueIds, @NonNull String updateSql, @NonNull String errorMsg, QueueStatus queueStatus) {
        String[] questions = new String[queueIds.size()];
        Arrays.fill(questions, "?");
        String questionsForIn = String.join(",", questions);

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(updateSql.replace("X", questionsForIn));) {
                long processedEpoch = Utility.getCurrentEpoch();

                if (QueueStatus.STARTED == queueStatus) processedEpoch = -1;

                if (QueueStatus.UNPROCESSED != queueStatus) {
                    stmt.setLong(1, processedEpoch);
                }
                else {
                    stmt.setNull(1, Types.BIGINT);
                }

                int index = 1;
                for (Long id : queueIds) {
                    stmt.setLong(++index, id);
                }

                dbVals.resultValue = stmt.executeUpdate();
            }
            catch (Exception ex) {
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
}
