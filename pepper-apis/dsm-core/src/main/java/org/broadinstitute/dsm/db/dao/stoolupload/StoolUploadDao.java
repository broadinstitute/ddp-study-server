package org.broadinstitute.dsm.db.dao.stoolupload;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.stoolupload.StoolUploadDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class StoolUploadDao implements Dao<StoolUploadDto> {
    private static final String SELECT_KIT_ID = "SELECT dsm_kit_id FROM ddp_kit INNER JOIN ddp_kit_request dkr"
            + " on ddp_kit.dsm_kit_request_id = dkr.dsm_kit_request_id";
    private static final String BY_BARCODE = " WHERE kit_label = ?";
    private static final String BY_PT_ID = " dkr.ddp_participant_id = ?";
    private static final String UPDATE_KIT = "UPDATE ddp_kit SET receive_date = ?, receive_by = 'HSPH'";
    private static final String BY_KIT_ID = " WHERE dsm_kit_id = ?";

    public Optional<StoolUploadDto> getStoolUploadDto(String participantId, String mfBarcode) {
        SimpleResult simpleResult = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_ID + BY_BARCODE + " AND " + BY_PT_ID)) {
                stmt.setString(1, mfBarcode);
                stmt.setString(2, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new StoolUploadDto(rs.getString(rs.getString(DBConstants.DSM_KIT_ID)));
                    } else {
                        throw new RuntimeException("No kit found with the barcode " + mfBarcode);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("No kit found with the barcode " + mfBarcode,
                    simpleResult.resultException);
        }
        return Optional.of((StoolUploadDto) simpleResult.resultValue);
    }

    public void updateKitData(String receiveDate, String kitId) {
        SimpleResult simpleResult = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT + BY_KIT_ID)) {
                stmt.setString(1, receiveDate);
                stmt.setString(2, kitId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error updating kit with id " + kitId,
                    simpleResult.resultException);
        }
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
