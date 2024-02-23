package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@TableName(
        name = DBConstants.DDP_ONC_HISTORY,
        alias = DBConstants.DDP_ONC_HISTORY_ALIAS,
        primaryKey = DBConstants.ONC_HISTORY_ID,
        columnPrefix = "")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OncHistory {

    private static final Logger logger = LoggerFactory.getLogger(OncHistory.class);

    private static final String SQL_UPDATE_ONC_HISTORY =
            "UPDATE ddp_onc_history SET created = ?, last_changed = ?, changed_by = ? WHERE participant_id = ? AND created IS NULL";

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private long participantId;

    @ColumnName(DBConstants.ONC_HISTORY_CREATED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String created;

    @ColumnName(DBConstants.ONC_HISTORY_REVIEWED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String reviewed;
    private String changedBy;

    public OncHistory() {
    }

    public OncHistory(long participantId, String created, String reviewed, String changedBy) {
        this.participantId = participantId;
        this.created = created;
        this.reviewed = reviewed;
        this.changedBy = changedBy;
    }

    /**
     * Set oncHistory created date for participant iff created date is null
     *
     * @return true if the created date was set, false if created date was already set or oncHistory record
     *              was not found
     */
    public static boolean setOncHistoryCreated(int participantId, String createdDate, String userId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_ONC_HISTORY)) {
                stmt.setString(1, createdDate);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, userId);
                stmt.setInt(4, participantId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Set oncHistory createdDate for participant {}", participantId);
                    return true;
                } else if (result == 0) {
                    logger.info("Did not update oncHistory createdDate for participant {}. "
                            + "Date was already set or oncHistory now found for participant", participantId);
                    return false;
                } else {
                    throw new DsmInternalError(String.format("Error setting oncHistory createdDate for participant %d."
                            + " Result row count: %d", participantId, result));
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error setting oncHistory createdDate of participant " + participantId, e);
            }
        });
    }

    public long getParticipantId() {
        return participantId;
    }

    public String getCreated() {
        return created;
    }

    public String getReviewed() {
        return reviewed;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
