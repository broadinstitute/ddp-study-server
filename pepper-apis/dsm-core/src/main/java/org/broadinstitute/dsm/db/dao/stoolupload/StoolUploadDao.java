package org.broadinstitute.dsm.db.dao.stoolupload;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.stoolupload.StoolUploadDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.lddp.db.SimpleResult;

public class StoolUploadDao implements Dao<StoolUploadDto> {
    private static final String SELECT_KIT_ID = "SELECT dsm_kit_id FROM ddp_kit INNER JOIN ddp_kit_request dkr"
            + " on ddp_kit.dsm_kit_request_id = dkr.dsm_kit_request_id";
    private static final String BY_BARCODE = " WHERE kit_label = ?";
    private static final String BY_PT_ID = " dkr.ddp_participant_id = ?";
    private static final String UPDATE_KIT = "UPDATE ddp_kit SET receive_date = ?, receive_by = 'HSPH'";
    private static final String BY_KIT_ID = " WHERE dsm_kit_id = ?";
    private static final String IF_NULL = " AND receive_date IS NULL";

    public Optional<StoolUploadDto> getStoolUploadDto(String participantId, String mfBarcode) {
        SimpleResult simpleResult = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_ID + BY_BARCODE + " AND " + BY_PT_ID)) {
                stmt.setString(1, mfBarcode);
                stmt.setString(2, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new StoolUploadDto(rs.getInt(DBConstants.DSM_KIT_ID));
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

    public void updateKitData(String receiveDate, int kitId, String mfBarcode) {
        SimpleResult simpleResult = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_KIT + BY_KIT_ID + IF_NULL)) {
                stmt.setString(1, receiveDate);
                stmt.setInt(2, kitId);
                int updateAmount = stmt.executeUpdate();

                if (updateAmount == 1) {
                    KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                            DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL),
                            mfBarcode, 1);

                    if (kitDDPNotification != null) {
                        EventUtil.triggerDDP(conn, kitDDPNotification);
                    }
                }

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
