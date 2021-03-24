package org.broadinstitute.ddp.util;

import com.google.api.client.http.HttpTransport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Form;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DMLException;
import org.broadinstitute.ddp.exception.DatStatKitRequestIdException;
import org.broadinstitute.ddp.exception.RecaptchaException;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Contains a collection of useful static methods.
 */
public class Utility
{
    private static final Logger logger = LoggerFactory.getLogger(Utility.class);

    private static final String LOG_PREFIX = "UTILITY - ";

    private static final String SQL_INSERT_SHORT_ID = "INSERT INTO SHORT_ID_LIST VALUES ()";
    public static final String SQL_DB_EXISTS = "SELECT 1";
    private static final String SQL_INSERT_KIT_REQUEST_ID = "INSERT INTO KIT_REQUEST (KITREQ_UUID) VALUES (?)";
    private static final String SQL_KIT_REQUEST_UUID_BY_REQID = "SELECT KITREQ_UUID FROM KIT_REQUEST WHERE KITREQ_ID = ?";
    private static final String SQL_ALL_KIT_REQUEST = "SELECT KITREQ_ID, KITREQ_UUID FROM KIT_REQUEST";
    private static final String SQL_ALL_KIT_REQUEST_GT_REQID = "SELECT KITREQ_ID, KITREQ_UUID FROM KIT_REQUEST WHERE KITREQ_ID > ? ";
    private static final String SQL_KIT_REQUEST_REQID_BY_UUID = "SELECT KITREQ_ID FROM KIT_REQUEST WHERE KITREQ_UUID = ?";

    private static final String[] VAULT_FIELDS = new String[] {
            "portal.environmentFromVault",
            "portal.easyPostKey",
            "portal.dbUrl",
            "portal.jwtSecret",
            "portal.jwtSalt",
            "portal.jwtMonitoringSecret",
            "portal.jwtDdpSecret",
            "portal.googleAuthClientKey",
            "portal.googleReCaptchaSecret",
            "email.key",
            "datStat.key",
            "datStat.secret",
            "datStat.username",
            "datStat.password"};

    private static final String[] AUTH0_FIELDS = new String[] {
            "auth0.account",
            "auth0.connections",
            "auth0.isSecretBase64Encoded",
            "auth0.ddpSecret",
            "auth0.ddpKey",
            "auth0.mgtSecret",
            "auth0.mgtKey",
            "auth0.mgtApiUrl"};

    private static final String[] SPLASH_VAULT_FIELDS = new String[] {
            "portal.environmentFromVault",
            "portal.dbUrl",
            "portal.jwtMonitoringSecret",
            "portal.jwtDdpSecret",
            "email.key"};

    private static final String[] SIMPLESERVICE_VAULT_FIELDS = new String[] {
            "portal.environmentFromVault",
            "portal.dbUrl",
            "portal.jwtMonitoringSecret"};

    private static final String[] DDP_LITE_FIELDS = new String[] {
            "portal.environmentFromVault",
            "portal.frontendAccessPwd",
            "portal.jwtSecret",
            "portal.jwtSalt",
            "portal.jwtMonitoringSecret",
            "portal.googleProjectCredentials"};

    public enum MockParticipant
    {
        EXISTING_PARTICIPANT, NEW_PARTICIPANT
    }

    public enum QueueStatus
    {
        UNPROCESSED, STARTED, PROCESSED
    }

    public enum Deployment
    {
        UNIT_TEST, LOCAL_DEV, REMOTE_DEV, UAT, PROD
    }

    public enum RequestMethod {
        GET, PATCH, POST, PUT
    }

    public static final String FILE_ARG_MISSING_MESSAGE = "No configuration file specified.";
    public static final String FILE_MISSING_MESSAGE = "Configuration file does not exist.";
    public static final String FILE_LOAD_ERROR_MESSAGE = "Problem loading configuration file: ";
    public static final String ENV_LOAD_ERROR_MESSAGE = "Problem loading ENV: ";
    public static final String LOAD_MISMATCH_ERROR_MESSAGE = "Configuration environment mismatch.";
    public static final String CONFIG_INCOMPLETE_ERROR_MESSAGE = "Required config setting is missing: ";
    public static final String CONFIG_AUTH_INCOMPLETE_ERROR_MESSAGE = "Required Auth0 config setting is missing: ";
    public static final String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'UTC'";
    public static final String EDC_DATE_FORMAT = "MM-dd-yyyy";
    public static final String DATE_FORMAT_SLASH = "MM/dd/yyyy";

    public static String getCurrentUTCDateTimeAsString()
    {
        SimpleDateFormat format = new SimpleDateFormat(UTC_DATE_FORMAT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    /***
     * Will return null if original date is null or the empty string.
     */
    public static String convertUTCDateTimeToOtherFormat(String originalDate, @NonNull String newFormat)
    {
        return dateConverter(originalDate, Utility.UTC_DATE_FORMAT, newFormat);
    }

    /***
     * Will return null if original date is null or the empty string.
     */
    public static String convertEDCDateToOtherFormat(String originalDate, @NonNull String newFormat)
    {
        return dateConverter(originalDate, Utility.EDC_DATE_FORMAT, newFormat);
    }

    public static String postToServer(@NonNull String url, @NonNull List<NameValuePair> postData)
    {
        String response = null;
        try
        {
            response = org.apache.http.client.fluent.Request.Post(url)
                    .bodyForm(postData).execute().returnContent().asString();
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to post request to server.", ex);
        }
        return response;
    }

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

    public static void verifyRecaptcha(@NonNull String recaptcha, @NonNull String secret, @NonNull String googleURL, String bypass, @NonNull String propertySource)
    {
        if (!BypassRecaptchaVerify(bypass, propertySource))
        {
            List<NameValuePair> postData = Form.form().add("secret", secret).add("response", recaptcha).build();

            String response = Utility.postToServer(googleURL, postData);

            JsonObject json = new JsonParser().parse(response).getAsJsonObject();

            JsonElement successElement = json.get("success");

            boolean success = false;

            if (!successElement.isJsonNull())
            {
                success = successElement.getAsBoolean();
            }

            if (!success) throw new RecaptchaException("ReCAPTCHA validation failure.");
        }
        else
        {
            logger.warn("Bypassing reCAPTCHA validation.");
        }
    }

    public static Config getApplicationConfig(@NonNull String[] args)
    {
        if (args.length == 0)
        {
            throw new RuntimeException(FILE_ARG_MISSING_MESSAGE);
        }

        File configFile = new File(args[0]);

        if (!configFile.exists()) throw new RuntimeException(FILE_MISSING_MESSAGE);

        Config originalConfig;
        Config mergedConfig;

        try
        {
            Config tempConfig = ConfigFactory.parseFile(configFile); //use a file here so file can be outside jar
            originalConfig = tempConfig.resolve(); //otherwise substitutions won't be made for environment variables
        }
        catch (Exception ex)
        {
            throw new RuntimeException(FILE_LOAD_ERROR_MESSAGE + ex);
        }

        Properties argProps = new Properties();

        //loop through the rest of the command line args
        for (int i = 1; i < args.length; i++)
        {
            if (args[i].contains("=="))
            {
                String[] parts = args[i].split("==");
                argProps.setProperty(parts[0].toString(), (parts.length == 2) ? parts[1].toString() : "");
            }
            else
            {
                throw new IllegalArgumentException("Argument " + args[i] + " does not contain ==.");
            }
        }

        //create a merged config from the old plus command line args
        if (argProps.size() > 0)
        {
            mergedConfig = ConfigFactory.parseProperties(argProps).withFallback(originalConfig);
        }
        else
        {
            mergedConfig = originalConfig;
        }


        String[] vaultFields = VAULT_FIELDS;

        if (mergedConfig.hasPath("portal.splashPageUI")) {
            if (mergedConfig.getBoolean("portal.splashPageUI")) {
                vaultFields = SPLASH_VAULT_FIELDS;
            }
        }
        else if (mergedConfig.hasPath("portal.simpleService")) {
            if (mergedConfig.getBoolean("portal.simpleService")) {
                vaultFields = SIMPLESERVICE_VAULT_FIELDS;
            }
        }
        else if (mergedConfig.hasPath("portal.ddpLite")) {
            if (mergedConfig.getBoolean("portal.ddpLite")) {
                vaultFields = DDP_LITE_FIELDS;
            }
        }

        //now check that we didn't miss any of the required secrets
        for (String propertyName : vaultFields)
        {
            if (StringUtils.isBlank(mergedConfig.getString(propertyName)))
            {
                throw new RuntimeException(CONFIG_INCOMPLETE_ERROR_MESSAGE + propertyName);
            }
        }

        //now check that we didn't miss any Auth0 secrets
        if (Auth0Util.useAuth0(mergedConfig)) {
            for (String propertyName : AUTH0_FIELDS) {
                if (propertyName.equals("auth0.connections")) {
                    if (mergedConfig.getList(propertyName).size() == 0) {
                        throw new RuntimeException(CONFIG_AUTH_INCOMPLETE_ERROR_MESSAGE + propertyName);
                    }
                }
                else if (propertyName.equals("auth0.isSecretBase64Encoded")) {
                    if (!mergedConfig.hasPath("auth0.isSecretBase64Encoded")) {
                        throw new RuntimeException(CONFIG_AUTH_INCOMPLETE_ERROR_MESSAGE + propertyName);
                    }
                }
                else if (StringUtils.isBlank(mergedConfig.getString(propertyName))) {
                    throw new RuntimeException(CONFIG_AUTH_INCOMPLETE_ERROR_MESSAGE + propertyName);
                }
            }
        }

        //let's make sure we grabbed the right vault settings for this environment
        if (!mergedConfig.getString("portal.environment").equals(mergedConfig.getString("portal.environmentFromVault")))
        {
            throw new RuntimeException(LOAD_MISMATCH_ERROR_MESSAGE);
        }


        return mergedConfig;
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

    /**
     * Handy method for just getting a count of records in a large DDP table.
     * @param sql
     * @return count
     * @
     */
    public static long getCountInBigTable(@NonNull String sql)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(sql))
            {
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (rs.next())
                    {
                        dbVals.resultValue = rs.getLong(1);
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

        return (Long)results.resultValue;
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

    /**
     * Inserts a row into the table that generates integer ids for participants and returns the id.
     * @return id for participant
     */
    public static int getNextShortParticipantId()
    {
        try
        {
            logger.info(LOG_PREFIX + "Adding short participant id...");
            return generateNextId(SQL_INSERT_SHORT_ID);
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying to get the next short participant id.", ex);
        }
    }

    /**
     * Inserts a row into the table that generates integer ids for kit request UUIDs.
     * @return kit request Id
     */
    public static int getNextKitRequestId()
    {
        try
        {
            logger.info(LOG_PREFIX + "Adding kit request id...");
            return generateAndStoreIdForUuid(SQL_INSERT_KIT_REQUEST_ID, java.util.UUID.randomUUID().toString());
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying to get the next kit request id.", ex);
        }
    }

    /**
     * Gets the kit request UUID using the request Id.
     * @return kit request UUID
     */
    public static String getKitRequestUuid(@NonNull int requestId)
    {
        try
        {
            logger.info(LOG_PREFIX + "Checking for uuid for kit request id...");
            return getUuidForStoredId(SQL_KIT_REQUEST_UUID_BY_REQID, requestId);
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying find the kit UUID for kit " + requestId + ".", ex);
        }
    }

    public static long getCurrentEpoch()
    {
        return System.currentTimeMillis()/1000;
    }

    /**
     * Inserts row with uuid into DDP table and returns the value of the auto incremented column for the row.
     * Assumes table only has two columns: one auto increment and one for a UUID.
     * @param sql should look something like: INSERT INTO KIT_REQUEST (KITREQ_UUID) VALUES (?)
     * @param uuid UUID to map to an id
     * @return id
     */
    private static int generateAndStoreIdForUuid(@NonNull String sql, @NonNull String uuid)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
            {
                stmt.setString(1, uuid);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys())
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

        if ((results.resultException != null)||((Integer)results.resultValue < 1))
        {
            throw new DMLException("An error occurred while trying to add uuid and retrieve id.", results.resultException);
        }

        return (Integer)results.resultValue;
    }

    /**
     * Gets all records in KIT_REQUEST table
     * @return map of uuid and request ID
     * @
     */
    public static Map<Integer,String> getAllKitRequests()  {
        try
        {
            logger.info(LOG_PREFIX + "Getting all records from KIT_REQUEST");
            return  (Map<Integer,String>)getAllKitRequestRecords(SQL_ALL_KIT_REQUEST,0);
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying fetch all records ", ex);
        }
    }

    /**
     * Gets all records in KIT_REQUEST table where reqId GT maxReqId
     * @return map of uuid and request ID
     */
    public static Map<Integer,String> getAllKitRequestsGTID(int maxReqId)  {
        try
        {
            logger.info(LOG_PREFIX + "Getting all records from KIT_REQUEST where req id is GT " + maxReqId);
            return  (Map<Integer,String>)getAllKitRequestRecords(SQL_ALL_KIT_REQUEST_GT_REQID, maxReqId);
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying fetch records ", ex);
        }
    }

    public static void removedUnprocessedFromQueue(@NonNull String identifier, @NonNull String deleteSql, @NonNull String errorMsg) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setString(1, identifier);
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

    public static void updateProcessedInQueue(@NonNull Long queueId, @NonNull String updateSql, @NonNull String errorMsg, QueueStatus queueStatus) {
        ArrayList<Long> queueIds = new ArrayList<>();
        queueIds.add(queueId);
        updateProcessedInQueue(queueIds, updateSql, errorMsg, queueStatus);
    }

    public static void updateProcessedInQueue(@NonNull ArrayList<Long> queueIds, @NonNull String updateSql, @NonNull String errorMsg, QueueStatus queueStatus) {
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

    /**
     * Assumes table only has two columns: one auto increment/Id and one for a UUID.
     * @param sql should look something like: SELECT KITREQ_ID, KITREQ_UUID FROM KIT_REQUEST
     * @return Map of KITREQ_ID and KITREQ_UUID
     */
    private static Map<Integer,String> getAllKitRequestRecords(@NonNull String sql, int maxVal)  {

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = new HashMap<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (maxVal > 0) {
                    stmt.setInt(1, maxVal);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ((Map<Integer, String>)dbVals.resultValue).put(rs.getInt(1), rs.getString(2));                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null)
        {
            throw new DMLException("An error occurred while trying to get kit request records.", results.resultException);
        }

        return (Map<Integer, String>)results.resultValue;
    }

    /**
     * Queries for the UUID for the given id.
     * Assumes table only has two columns: one auto increment/Id and one for a UUID.
     * @param sql should look something like: SELECT KITREQ_UUID FROM KIT_REQUEST WHERE KITREQ_ID = ?
     * @param id that should have a UUID associated with it
     * @return UUID
     */
    private static String getUuidForStoredId(@NonNull String sql, @NonNull int id)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(sql))
            {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery())
                {
                    int count = 1;
                    while (rs.next())
                    {
                        if (count++ > 1) {
                            throw new RuntimeException("Only one row expected.");
                        }
                        dbVals.resultValue = rs.getString(1);
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
            throw new DMLException("An error occurred while trying to find id for uuid.", results.resultException);
        }

        return (String)results.resultValue;
    }

    /**
     * Queries for the id for the given UUID.
     * Assumes table only has two columns: one auto increment/Id and one for a UUID.
     * @param sql should look something like: SELECT KITREQ_ID FROM KIT_REQUEST WHERE KITREQ_UUID = ?
     * @param uuid
     * @return id
     */
    private static int getIdForStoredUuid(@NonNull String sql, @NonNull String uuid)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(sql))
            {
                stmt.setString(1, uuid);
                try (ResultSet rs = stmt.executeQuery())
                {
                    int count = 1;
                    while (rs.next())
                    {
                        if (count++ > 1) {
                            throw new RuntimeException("Only one row expected.");
                        }
                        dbVals.resultValue = rs.getString(1);
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
            throw new DMLException("An error occurred while trying to get id for uuid.", results.resultException);
        }

        return Integer.parseInt((String)results.resultValue);
    }

    /**
     * Gets the kit request UUID using the request Id.
     * @return kit request UUID
     */
    public static int getKitRequestByUuid(@NonNull String UUID)  {
        try
        {
            logger.info(LOG_PREFIX + "Checking for reqId by UUID...");
            return getIdForStoredUuid(SQL_KIT_REQUEST_REQID_BY_UUID, UUID);
        }
        catch (Exception ex)
        {
            throw new DatStatKitRequestIdException("An error occurred trying find the kit req for uuid " + UUID + ".", ex);
        }
    }

    private static boolean BypassRecaptchaVerify(String bypass, @NonNull String propertySource)
    {
        //we will only consider doing this bypass for unit testing
        if (!propertySource.equals(Deployment.UNIT_TEST.toString())){
            return false;
        }

        return ((bypass != null)&&(bypass.equals("1")));
    }

    /**
     * Turns out google's verbose HTTP logging, which is useful
     * for looking at headers, body, etc.
     */
    public static void enableGoogleHTTPLogging() {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HttpTransport.class.getName());
        logger.setLevel(Level.CONFIG);
        logger.addHandler(new Handler() {

            @Override
            public void close() throws SecurityException {
            }

            @Override
            public void flush() {
            }

            @Override
            public void publish(LogRecord record) {
                // default ConsoleHandler will print &gt;= INFO to System.err
                if (record.getLevel().intValue() < Level.INFO.intValue()) {
                    System.out.println(record.getMessage());
                }
            }
        });
    }

    /**
     * Converts month name to a 1-based month number (1-12) for datstat
     */
    public static int monthNameToDatStatNumber(String monthName) {
        DateFormatSymbols dateSymbols = new DateFormatSymbols();
        String[] months = dateSymbols.getMonths();
        int monthNumber = -1;
        for (int monthIndex = 0; monthIndex < months.length; monthIndex++) {
            if (months[monthIndex].equalsIgnoreCase(monthName)) {
                monthNumber = monthIndex + 1;
            }
        }
        if (monthNumber == -1) {
            throw new RuntimeException("Cannot find month number for " + monthName);
        }
        return monthNumber;
    }

    /**
     * Converts 1-12 month number from datstat to month name
     */
    public static String monthNumberToMonthName(int monthNumber) {
        String[] months = new DateFormatSymbols().getMonths();
        if (monthNumber < 1 || monthNumber > months.length) {
            throw new RuntimeException("Month number must be between 1 and " + months.length);
        }
        return months[monthNumber -1];
    }

    public static String getHttpResponseAsString(HttpResponse response) throws Exception {
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }

    /**
     * Inserts row into a simple DDP table with just an auto incremented column and returns the value of the auto incremented column.
     * Assumes table only has one column: one auto increment
     * @param sql should look something like: INSERT INTO SHORT_ID_LIST VALUES ()
     * @return id
     */
    public static int generateNextId(@NonNull String sql)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
            {
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys())
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

        if ((results.resultException != null)||((Integer)results.resultValue < 1))
        {
            throw new DMLException("An error occurred while trying to add and retrieve id.", results.resultException);
        }

        return (Integer)results.resultValue;
    }

    private static String dateConverter(String originalDate, @NonNull String oldFormat, @NonNull String newFormat)
    {
        if (StringUtils.isNotBlank(originalDate)) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(oldFormat);
                Date date = formatter.parse(originalDate);
                return new SimpleDateFormat(newFormat).format(date);
            }
            catch (Exception ex) {
                throw new RuntimeException("Unable to convent date " + originalDate + " to format: " + newFormat, ex);
            }
        }
        else {
            return null;
        }
    }
}
