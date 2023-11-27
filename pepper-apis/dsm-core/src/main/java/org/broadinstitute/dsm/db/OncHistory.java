package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.NonNull;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.lddp.db.SimpleResult;
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

    public static NameValue setOncHistoryCreated(@NonNull String participantId, @NonNull String userId) {
        String createdDate = SystemUtil.getDateFormatted(System.currentTimeMillis());
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_ONC_HISTORY)) {
                stmt.setString(1, createdDate);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, userId);
                stmt.setString(4, participantId);
                int result = stmt.executeUpdate();
                // 0 is also fine, because then created was already set
                if (result == 1) {
                    logger.info("Set oncHistoryDetails created for participant " + participantId);
                    dbVals.resultValue = createdDate;
                } else if (result == 0) {
                    logger.info("OncHistory was already set");
                    dbVals.resultValue = null;
                } else {
                    throw new RuntimeException(
                            "Error setting oncHistoryDetails of participant " + participantId + " it was updating " + result + " rows");
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating oncHistoryDetails ", results.resultException);
        }
        return new NameValue("o.created", results.resultValue);
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
