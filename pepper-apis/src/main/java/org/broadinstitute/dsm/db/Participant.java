package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class Participant {

    private static final Logger logger = LoggerFactory.getLogger(Participant.class);

    public static final String SQL_SELECT_PARTICIPANT = "SELECT p.participant_id, p.ddp_participant_id, p.assignee_id_mr, p.assignee_id_tissue, p.ddp_instance_id, " +
            "realm.instance_name, realm.base_url, realm.mr_attention_flag_d, realm.tissue_attention_flag_d, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, " +
            "o.onc_history_id, o.created, o.reviewed, " +
            "r.cr_sent, r.cr_received, r.notes, r.minimal_mr, r.abstraction_ready, r.additional_values_json, ex.exit_date, ex.exit_by " +
            "FROM ddp_participant p LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_onc_history o on (o.participant_id = p.participant_id) " +
            "LEFT JOIN ddp_participant_record r on (r.participant_id = p.participant_id) " +
            "LEFT JOIN ddp_participant_exit ex on (p.ddp_participant_id = ex.ddp_participant_id AND p.ddp_instance_id = ex.ddp_instance_id) " +
            "WHERE realm.instance_name = ? ";

    private final String participantId;
    private final String ddpParticipantId;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT,
            alias = DBConstants.DDP_PARTICIPANT_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ASSIGNEE_ID_MR)
    private final String assigneeMr;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT,
            alias = DBConstants.DDP_PARTICIPANT_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ASSIGNEE_ID_TISSUE)
    private final String assigneeTissue;
    private final String realm;

    @TableName (
            name = DBConstants.DDP_ONC_HISTORY,
            alias = DBConstants.DDP_ONC_HISTORY_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ONC_HISTORY_CREATED)
    private final String createdOncHistory;

    @TableName (
            name = DBConstants.DDP_ONC_HISTORY,
            alias = DBConstants.DDP_ONC_HISTORY_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ONC_HISTORY_REVIEWED)
    private final String reviewedOncHistory;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.CR_SENT)
    private final String paperCRSent;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.CR_RECEIVED)
    private final String paperCRReceived;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.NOTES)
    private final String ptNotes;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.MINIMAL_MR)
    private final boolean minimalMR;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ABSTRACTION_READY)
    private final boolean abstractionReady;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_RECORD,
            alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.ADDITIONAL_VALUES)
    private final String additionalValues;

    @TableName (
            name = DBConstants.DDP_PARTICIPANT_EXIT,
            alias = DBConstants.DDP_PARTICIPANT_EXIT_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName (DBConstants.EXIT_DATE)
    private final long exitDate;

    public Participant(String participantId, String ddpParticipantId, String assigneeMr, String assigneeTissue, String instanceName,
                       String createdOncHistory, String reviewedOncHistory, String paperCRSent, String paperCRReceived, String ptNotes,
                       boolean minimalMR, boolean abstractionReady, String additionalValues, long exitDate) {
        this.participantId = participantId;
        this.ddpParticipantId = ddpParticipantId;
        this.assigneeMr = assigneeMr;
        this.assigneeTissue = assigneeTissue;
        this.realm = instanceName;
        this.createdOncHistory = createdOncHistory;
        this.reviewedOncHistory = reviewedOncHistory;
        this.paperCRSent = paperCRSent;
        this.paperCRReceived = paperCRReceived;
        this.ptNotes = ptNotes;
        this.minimalMR = minimalMR;
        this.abstractionReady = abstractionReady;
        this.additionalValues = additionalValues;
        this.exitDate = exitDate;
    }

    public static Participant getParticipant(@NonNull Map<String, Assignee> assignees, @NonNull String realm, @NonNull ResultSet rs) throws SQLException {
        String assigneeMR = null;
        String assigneeTissue = null;
        if (assignees != null && !assignees.isEmpty()) {
            String assigneeIdMR = rs.getString(DBConstants.ASSIGNEE_ID_MR);
            if (StringUtils.isNotBlank(assigneeIdMR)) {
                assigneeMR = assignees.get(assigneeIdMR).getName();
            }
            String assigneeIdTissue = rs.getString(DBConstants.ASSIGNEE_ID_TISSUE);
            if (StringUtils.isNotBlank(assigneeIdTissue)) {
                assigneeTissue = assignees.get(assigneeIdTissue).getName();
            }
        }
        Participant participant = new Participant(rs.getString(DBConstants.PARTICIPANT_ID),
                rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                assigneeMR, assigneeTissue, realm,
                rs.getString(DBConstants.ONC_HISTORY_CREATED),
                rs.getString(DBConstants.ONC_HISTORY_REVIEWED),
                rs.getString(DBConstants.CR_SENT),
                rs.getString(DBConstants.CR_RECEIVED),
                rs.getString(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                rs.getBoolean(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.MINIMAL_MR),
                rs.getBoolean(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ABSTRACTION_READY),
                rs.getString(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ADDITIONAL_VALUES),
                rs.getLong(DBConstants.EXIT_DATE));
        return participant;
    }

    public static Map<String, Participant> getParticipants(@NonNull String realm) {
        return getParticipants(realm, null);
    }

    public static Map<String, Participant> getParticipants(@NonNull String realm, String queryAddition) {
        logger.info("Collection participant information");
        Map<String, Participant> participants = new HashMap<>();
        HashMap<String, Assignee> assignees = Assignee.getAssigneeMap(realm);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_PARTICIPANT, queryAddition))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participants.put(rs.getString(DBConstants.DDP_PARTICIPANT_ID), getParticipant(assignees, realm, rs));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of participants ", results.resultException);
        }
        logger.info("Got " + participants.size() + " participants in DSM DB for " + realm);
        return participants;
    }
}
