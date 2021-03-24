package org.broadinstitute.ddp.datstat;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.exception.DMLException;
import org.broadinstitute.ddp.util.DeliveryAddress;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class Participant {
    private static final Logger logger = LoggerFactory.getLogger(Participant.class);

    private static final String LOG_PREFIX = "DB PARTICIPANT - ";

    private static final String SQL_MAX_MODIFIED = "SELECT EXTERNAL_MODIFIED FROM PARTICIPANT WHERE PART_MODIFIED = (SELECT MAX(PART_MODIFIED) FROM PARTICIPANT)";
    private static final String SQL_PARTICIPANT_EXISTS = "SELECT COUNT(*) FROM PARTICIPANT WHERE EXTERNAL_ID = ?";
    private static final String SQL_UPDATE_PARTICIPANT = "UPDATE PARTICIPANT SET PART_CURRENT_STATUS = ?, PART_EMAIL = ?, PART_FIRST_NAME = ?, " +
            "PART_LAST_NAME = ?, PART_ADDRESS_VALID = ?, PART_STREET1 = ?, PART_STREET2 = ?, PART_CITY = ?, PART_STATE = ?, PART_POSTAL_CODE = ?, " +
            "PART_COUNTRY = ?, PART_SHORTID = ?, PART_EXITED = ?, PART_MODIFIED = ?, EXTERNAL_MODIFIED = ? " +
            "WHERE EXTERNAL_ID = ?";
    private static final String SQL_INSERT_PARTICIPANT = "INSERT INTO PARTICIPANT (EXTERNAL_ID, PART_CURRENT_STATUS, PART_EMAIL, PART_FIRST_NAME, PART_LAST_NAME, " +
            "PART_ADDRESS_VALID, PART_STREET1, PART_STREET2, PART_CITY, PART_STATE, PART_POSTAL_CODE, PART_COUNTRY, PART_SHORTID, PART_EXITED, PART_CREATED, PART_MODIFIED, EXTERNAL_MODIFIED) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_PARTICIPANT_LIST = "SELECT EXTERNAL_ID, PART_CURRENT_STATUS, PART_EMAIL, PART_FIRST_NAME, PART_LAST_NAME, " +
            "PART_ADDRESS_VALID, PART_STREET1, PART_STREET2, PART_CITY, PART_STATE, PART_POSTAL_CODE, PART_COUNTRY, PART_SHORTID, PART_EXITED, " +
            "PART_MODIFIED, EXTERNAL_MODIFIED FROM PARTICIPANT";
    private static final String SQL_PARTICIPANT = SQL_PARTICIPANT_LIST + " WHERE EXTERNAL_ID = ?";
    private static final String SQL_PARTICIPANTS_WITH_STATUS = SQL_PARTICIPANT_LIST + " WHERE PART_CURRENT_STATUS = ?";
    private static final String PARTICIPANT_DML_ERROR = "Record %s failed for participant.";
    private static final String PARTICIPANT_DML_RECORD_ERROR = "No records affected by %s.";

    public static final int TWO_MINUTES = 2 * 60 * 1000;
    public static final String DEFAULT_LAST_MODIFIED = "2000-01-01T00:00:00.000";

    private String altPid;
    private String currentStatus;
    private String email;
    private String firstName;
    private String lastName;
    private int addressValid;
    private DeliveryAddress address;
    private String shortId;
    private String exited;
    private Long dateModified;
    private String externalModified;

    private enum ParticipantQuery
    {
        EXTERNAL_ID(1), PART_CURRENT_STATUS(2), PART_EMAIL(3), PART_FIRST_NAME(4), PART_LAST_NAME(5), PART_ADDRESS_VALID(6), PART_STREET1(7),
        PART_STREET2(8), PART_CITY(9), PART_STATE(10), PART_POSTAL_CODE(11), PART_COUNTRY(12), PART_SHORTID(13), PART_EXITED(14), PART_MODIFIED(15),
        EXTERNAL_MODIFIED(16);

        private final int idx;

        ParticipantQuery(int idx)  { this.idx = idx;}
        public int getIdx() { return idx;}
    }

    public Participant() {
    }

    public Participant(@NonNull String altPid, @NonNull String currentStatus, @NonNull String email, String firstName, String lastName,
                       int addressValid, DeliveryAddress address, String shortId, String exited, @NonNull Long dateModified,
                       @NonNull String externalModified) {
        this.altPid = altPid;
        this.currentStatus = currentStatus;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.addressValid = addressValid;
        this.address = address;
        this.shortId = shortId;
        this.exited = exited;
        this.dateModified = dateModified;
        this.externalModified = externalModified;
    }

    public static Participant getParticipantFromDb(@NonNull String altPid) {
        Map<String, Participant> participants = getParticipantsFromDb(altPid, SQL_PARTICIPANT);

        if (participants.isEmpty()) {
            return null;
        }
        else if (participants.size() > 1) {
            throw new DMLException("Too many participants found with altpid: " + altPid);
        }
        else {
            return (Participant)participants.values().toArray()[0];
        }
    }

    public static Map<String, Participant> getParticipantsFromDbWithStatus(@NonNull String status) {
        return getParticipantsFromDb(status, SQL_PARTICIPANTS_WITH_STATUS);
    }

    public static Map<String, Participant> getAllParticipantsFromDb() {
        return getParticipantsFromDb(null, SQL_PARTICIPANT_LIST);
    }

    public static boolean participantExists(@NonNull String altPid)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            checkDbForParticipant(dbVals, conn, altPid);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException("An error occurred while attempting to find a participant.", results.resultException);
        }

        return ((Integer)results.resultValue > 0);
    }

    public static void addParticipant(@NonNull JsonObject participantJson)
    {
        performDmlWithTransaction(participantJson, false);
    }

    public static void updateParticipant(@NonNull JsonObject participantJson)
    {
        performDmlWithTransaction(participantJson, true);
    }

    public static String getAdjustedMaxModified() {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_MAX_MODIFIED)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
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
            throw new DMLException("An error occurred while retrieving the max participant modified date.", results.resultException);
        }

        if (results.resultValue != null) {
            try {
                return adjustDate((String) results.resultValue, -TWO_MINUTES);
            }
            catch (Exception ex) {
                throw new RuntimeException("An error occurred calculating the adjusted last modified date.", ex);
            }
        }
        else { //nothing in DB yet so return a really early date
            return DEFAULT_LAST_MODIFIED;
        }
    }

    public static String adjustDate(String originalDate, int adjustmentInMilliseconds) throws Exception {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date(format.parse(originalDate).getTime() + adjustmentInMilliseconds);
        return format.format(date);
    }

    public static boolean syncParticipantData(@NonNull JsonObject participantJson) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            getParticipantsFromDb(dbVals, conn, participantJson.get(DatStatUtil.ALTPID_FIELD).getAsString(), SQL_PARTICIPANT);

            if (dbVals.resultException != null) {
                return dbVals;
            }

            Map<String, Participant> participants = (Map<String, Participant>)dbVals.resultValue;
            boolean participantExists = false;

            if (participants.size() == 1) {
                participantExists = true;
                String dbLastModified = ((Participant)(participants.values().toArray()[0])).getExternalModified();

                //if last modified hasn't changed then we don't need to perform an update at all
                if (dbLastModified.equals(participantJson.get("DATSTAT_LASTMODIFIED").getAsString())) {
                    dbVals.resultValue = 0;
                    return dbVals;
                }
            }

            performDml(dbVals, conn, participantJson, participantExists);

            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException("An error occurred syncing participant data.", results.resultException);
        }

        return ((Integer)results.resultValue == 1);
    }

    private static void checkDbForParticipant(@NonNull SimpleResult dbVals, @NonNull Connection conn, @NonNull String altPid) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_PARTICIPANT_EXISTS);) {
            stmt.setString(1, altPid);
            try (ResultSet rs = stmt.executeQuery();) {
                while (rs.next()) {
                    dbVals.resultValue = rs.getInt(1);
                }
            }
        }
        catch (Exception ex) {
            dbVals.resultException = new RuntimeException("An error occurred trying to find the participant.", ex);
        }
    }

    private static boolean performDmlWithTransaction(@NonNull JsonObject participantJson, boolean participantExists) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            performDml(dbVals, conn, participantJson, participantExists);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException("An error occurred adding/updating participant data.", results.resultException);
        }

        return ((Integer)results.resultValue == 1);
    }

    private static void performDml(@NonNull SimpleResult dbVals, @NonNull Connection conn, @NonNull JsonObject participantJson, boolean participantExists) {
        try (PreparedStatement stmt = conn.prepareStatement((participantExists) ? SQL_UPDATE_PARTICIPANT: SQL_INSERT_PARTICIPANT)) {
            long currentTimeMs = System.currentTimeMillis();
            int parameter = 1;
            if (!participantExists) {
                stmt.setString(parameter++, participantJson.get(DatStatUtil.ALTPID_FIELD).getAsString());
            }
            stmt.setString(parameter++, participantJson.get("DDP_CURRENT_STATUS").getAsString());
            stmt.setString(parameter++, participantJson.get("DATSTAT_EMAIL").getAsString());
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DATSTAT_FIRSTNAME", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DATSTAT_LASTNAME", participantJson));
            stmt.setInt(parameter++, participantJson.get("DDP_ADDRESS_VALID").getAsInt());
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_STREET1", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_STREET2", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_CITY", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_STATE", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_POSTAL_CODE", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_COUNTRY", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_PARTICIPANT_SHORTID", participantJson));
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DDP_EXITED", participantJson));
            if (!participantExists) {
                stmt.setLong(parameter++, currentTimeMs);
            }
            stmt.setLong(parameter++, currentTimeMs);
            stmt.setString(parameter++, DatStatUtil.getDatStatStringValue("DATSTAT_LASTMODIFIED", participantJson));
            if (participantExists) {
                stmt.setString(parameter++, participantJson.get(DatStatUtil.ALTPID_FIELD).getAsString());
            }
            dbVals.resultValue = stmt.executeUpdate();
        }
        catch (Exception ex) {
            dbVals.resultException = new DMLException(String.format(PARTICIPANT_DML_ERROR, (participantExists) ? "update" : "insert"), ex);
        }

        if ((dbVals.resultException == null)&&((Integer)dbVals.resultValue != 1)) {
            dbVals.resultException = new DMLException(String.format(PARTICIPANT_DML_RECORD_ERROR, (participantExists) ? "update" : "insert"));
        }
    }

    private static Map<String, Participant> getParticipantsFromDb(String param, @NonNull String sql) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            getParticipantsFromDb(dbVals, conn, param, sql);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException("An error occurred while retrieving participant(s).", results.resultException);
        }

        return (Map<String, Participant>)results.resultValue;
    }

    private static void getParticipantsFromDb(@NonNull SimpleResult dbVals, @NonNull Connection conn, String param, String sql) {
        Map<String, Participant> participants = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (param != null) stmt.setString(1, param);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    participants.put(rs.getString(ParticipantQuery.EXTERNAL_ID.getIdx()),
                            new Participant(rs.getString(ParticipantQuery.EXTERNAL_ID.getIdx()),
                                    rs.getString(ParticipantQuery.PART_CURRENT_STATUS.getIdx()),
                                    rs.getString(ParticipantQuery.PART_EMAIL.getIdx()),
                                    formatStringDbValue(rs, ParticipantQuery.PART_FIRST_NAME.getIdx()),
                                    formatStringDbValue(rs, ParticipantQuery.PART_LAST_NAME.getIdx()),
                                    rs.getInt(ParticipantQuery.PART_ADDRESS_VALID.getIdx()),
                                    new DeliveryAddress(formatStringDbValue(rs, ParticipantQuery.PART_STREET1.getIdx()),
                                            formatStringDbValue(rs, ParticipantQuery.PART_STREET2.getIdx()),
                                            formatStringDbValue(rs, ParticipantQuery.PART_CITY.getIdx()),
                                            formatStringDbValue(rs, ParticipantQuery.PART_STATE.getIdx()),
                                            formatStringDbValue(rs, ParticipantQuery.PART_POSTAL_CODE.getIdx()),
                                            formatStringDbValue(rs, ParticipantQuery.PART_COUNTRY.getIdx())),
                                    formatStringDbValue(rs, ParticipantQuery.PART_SHORTID.getIdx()),
                                    formatStringDbValue(rs, ParticipantQuery.PART_EXITED.getIdx()),
                                    (rs.getLong(ParticipantQuery.PART_MODIFIED.getIdx())/1000), //turn into epoch
                                    rs.getString(ParticipantQuery.EXTERNAL_MODIFIED.getIdx())));
                }
            }
            dbVals.resultValue = participants;
        }
        catch (Exception ex) {
            dbVals.resultException = ex;
        }
    }

    private static String formatStringDbValue(ResultSet rs, int column) throws Exception {
        return (rs.getObject(column) != null) ? rs.getString(column) : null;
    }
}
