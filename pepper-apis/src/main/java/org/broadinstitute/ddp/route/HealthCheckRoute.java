package org.broadinstitute.ddp.route;

import static spark.Spark.halt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Performs a database health check by attempting to execute a simple query.
 * Returns a JSON body containing a result and its explanation
 * Example: curl localhost:5555/healthcheck
 */
public class HealthCheckRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckRoute.class);

    private static final String HC_QUERY = "SELECT 1 FROM umbrella";
    private static final Integer QUERY_TIMEOUT = 20;
    private static final String CUSTOM_PASSWORD_HEADER = "Host";
    private static final long MIN_TIME_BETWEEN_DB_QUERIES = 2 * 1000L;
    private static AtomicLong lastRealHealthCheckTime = new AtomicLong(System.currentTimeMillis());
    private static final Object healthCheckMonitor = new Object();
    private static final AtomicBoolean isAThreadQueryingTheDatabase = new AtomicBoolean(false);

    /**
     * Checks the database health by attempting to execute a trivial query.
     *
     * @param conn connecton
     * @result A health check result code
     */
    private Integer checkHealth(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(HC_QUERY)) {
            stmt.setQueryTimeout(QUERY_TIMEOUT);
            try (ResultSet rs = stmt.executeQuery()) {
                return HealthCheckResponse.HC_OK;
            }
        } catch (SQLTimeoutException e) {
            return HealthCheckResponse.HC_QUERY_TIMED_OUT;
        } catch (SQLException e) {
            return HealthCheckResponse.HC_UNKNOWN_ERROR;
        }
    }

    @Override
    public HealthCheckResponse handle(Request request, Response response) throws Exception {

        // don't query the db on each healthcheck because google's redundant healthchecks tend to hammer us
        // all at once and we don't want this to exhaust our connection pool
        boolean shouldQueryDatabase = false;
        synchronized (healthCheckMonitor) {
            if (hasEnoughTimeElapsed() && !isAThreadQueryingTheDatabase.get()) {
                shouldQueryDatabase = true;
                isAThreadQueryingTheDatabase.set(true);
            }
        }
        if (shouldQueryDatabase) {
            Integer result = TransactionWrapper.withTxn(handle -> checkHealth(handle.getConnection()));
            lastRealHealthCheckTime.set(System.currentTimeMillis());
            isAThreadQueryingTheDatabase.set(false);
            return new HealthCheckResponse(result);
        } else {
            // if we don't do a real healthcheck, we're going to be optimistic
            return new HealthCheckResponse(HealthCheckResponse.HC_OK);
        }
    }

    /**
     * Returns true if enough time has elapsed inbetween queries
     * to the database
     */
    private boolean hasEnoughTimeElapsed() {
        return (System.currentTimeMillis() - lastRealHealthCheckTime.get()) > MIN_TIME_BETWEEN_DB_QUERIES;
    }
}
