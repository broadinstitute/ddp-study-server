package org.broadinstitute.dsm.db.dao.stoolupload;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoolUploadDao implements Dao<StoolUploadDto> {

    private static final Logger logger = LoggerFactory.getLogger(StoolUploadDao.class);

    private static final String UPDATE_KIT_QUERY = "UPDATE ddp_kit as dk, (SELECT dsm_kit_id FROM ddp_kit INNER JOIN ddp_kit_request dkr "
            + "on ddp_kit.dsm_kit_request_id = dkr.dsm_kit_request_id  WHERE kit_label = ? "
            + "AND dkr.ddp_participant_id = ?) as dki "
            + "SET receive_date = ?, receive_by = 'HSPH' "
            + "WHERE dk.dsm_kit_id = dki.dsm_kit_id AND receive_date IS NULL";


    public boolean updateKit(StoolUploadDto stoolUploadDto) {
        String receiveDate = stoolUploadDto.getReceiveDate();
        String kitLabel = stoolUploadDto.getMfBarcode();
        String ddpParticipantId = stoolUploadDto.getParticipantId();
        SimpleResult simpleResult = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT_QUERY)) {
                stmt.setString(1, kitLabel);
                stmt.setString(2, ddpParticipantId);
                stmt.setString(3, receiveDate);
                int updateAmount = stmt.executeUpdate();
                if (updateAmount != 1) {
                    throw new RuntimeException(String.format(
                            "Updating kit data failed for the participant with guid %s and kit label %s", ddpParticipantId, kitLabel));
                } else {
                    logger.info(String.format(
                            "Updated kit data successfully for the participant with guid %s and kit label %s", ddpParticipantId, kitLabel));
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error updating kit data for participant with guid " + ddpParticipantId,
                    simpleResult.resultException);
        }
        return true;
    }


    @Override
    public int create(StoolUploadDto stoolUploadDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<StoolUploadDto> get(long id) {
        return Optional.empty();
    }

}
