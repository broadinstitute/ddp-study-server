package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class ParticipantStatus {

    private static final String SQL_SELECT_MR_STATUS = "SELECT UNIX_TIMESTAMP(str_to_date(min(med.fax_sent), '%Y-%m-%d')) as mrRequested, UNIX_TIMESTAMP(str_to_date(min(med.mr_received), '%Y-%m-%d')) as mrReceived, " +
            "UNIX_TIMESTAMP(str_to_date(min(onc.fax_sent), '%Y-%m-%d')) as tissueRequested, UNIX_TIMESTAMP(str_to_date(min(onc.tissue_received), '%Y-%m-%d')) as tissueReceived, " +
            "UNIX_TIMESTAMP(str_to_date(min(tis.sent_gp), '%Y-%m-%d')) as tissueSent FROM ddp_medical_record med " +
            "LEFT JOIN ddp_institution inst on (med.institution_id = inst.institution_id) LEFT JOIN ddp_participant as part on (part.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_onc_history_detail onc on (med.medical_record_id = onc.medical_record_id) LEFT JOIN ddp_tissue tis on (tis.onc_history_detail_id = onc.onc_history_detail_id) " +
            "WHERE part.ddp_participant_id = ? AND part.ddp_instance_id = ? AND NOT med.deleted <=> 1 GROUP BY ddp_participant_id";

    private Long mrRequested;
    private Long mrReceived;
    private Long tissueRequested;
    private Long tissueReceived;
    private Long tissueSent;
    private List<KitStatus> samples;

    public static ParticipantStatus getParticipantStatus(@NonNull String ddpParticipantId, @NonNull String instanceId) {
        ParticipantStatus participantStatus = new ParticipantStatus();
        getMRStatus(ddpParticipantId, instanceId, participantStatus);
        participantStatus.setSamples(KitStatus.getSampleStatus(ddpParticipantId, instanceId));
        return participantStatus;
    }

    private static void getMRStatus(@NonNull String ddpParticipantId, String instanceId, ParticipantStatus participantStatus) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MR_STATUS)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Object mrRequested = rs.getObject(DBConstants.MR_REQUESTED);
                        Object mrReceived = rs.getObject(DBConstants.M_RECEIVED);
                        Object tissueRequested = rs.getObject(DBConstants.TISSUE_REQUESTED);
                        Object tissueReceived = rs.getObject(DBConstants.T_RECEIVED);
                        Object tissueSent = rs.getObject(DBConstants.TISSUE_SENT);
                        participantStatus.setMrRequested(mrRequested != null ? (Long) mrRequested : null);
                        participantStatus.setMrReceived(mrReceived != null ? (Long) mrReceived : null);
                        participantStatus.setTissueRequested(tissueRequested != null ? (Long) tissueRequested : null);
                        participantStatus.setTissueReceived(tissueReceived != null ? (Long) tissueReceived : null);
                        participantStatus.setTissueSent(tissueSent != null ? (Long) tissueSent : null);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of assignees ", results.resultException);
        }
    }
}
