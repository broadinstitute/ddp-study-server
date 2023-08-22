package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class KitStatusDao implements Dao<NonPepperKitStatusDto> {

    private static final String SELECT_KIT_STATUS =
            " SELECT req.*, k.*, discard.*, tracking.tracking_id as return_tracking_number, tracking.scan_by as tracking_scan_by, "
                    + " tracking.scan_date as tracking_scan_date FROM ddp_kit_request req "
                    + " LEFT JOIN ddp_kit k on (k.dsm_kit_request_id = req.dsm_kit_request_id) "
                    + " LEFT JOIN ddp_kit_discard discard on  (discard.dsm_kit_request_id = req.dsm_kit_request_id) "
                    + " LEFT JOIN ddp_kit_tracking tracking on  (tracking.kit_label = k.kit_label) ";

    private static final String BY_INSTANCE_ID = " WHERE ddp_instance_id = ?";
    private static final String BY_JUNIPER_KIT_ID = " WHERE ddp_kit_request_id = ?";
    private static final String BY_PARTICIPANT_ID = " WHERE ddp_participant_id = ?";

    @Override
    public int create(NonPepperKitStatusDto nonPepperKitStatusDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<NonPepperKitStatusDto> get(long id) {
        return Optional.empty();
    }

    public List<NonPepperKitStatusDto> getKitsByInstanceId(DDPInstance ddpInstance, Map<Integer, UserDto> users) {
        List<NonPepperKitStatusDto> list = new ArrayList<>();
        BuildNonPepperKitStatusDto builder = new BuildNonPepperKitStatusDto();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_INSTANCE_ID))) {
                stmt.setString(1, ddpInstance.getDdpInstanceId());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(builder.build(rs, users));
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits for %s", ddpInstance.getDdpInstanceId()));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DsmInternalError(simpleResult.resultException);
        }
        return list;

    }

    public List<NonPepperKitStatusDto> getKitsByJuniperKitId(String juniperKitId, Map<Integer, UserDto> users) {
        List<NonPepperKitStatusDto> list = new ArrayList<>();
        BuildNonPepperKitStatusDto builder = new BuildNonPepperKitStatusDto();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_JUNIPER_KIT_ID))) {
                stmt.setString(1, juniperKitId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(builder.build(rs, users));
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits with juniper kit id %s", juniperKitId));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DsmInternalError(simpleResult.resultException);
        }
        return list;
    }

    public List<NonPepperKitStatusDto> getKitsByParticipantId(String participantId, Map<Integer, UserDto> users) {
        List<NonPepperKitStatusDto> list = new ArrayList<>();
        BuildNonPepperKitStatusDto builder = new BuildNonPepperKitStatusDto();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_STATUS.concat(BY_PARTICIPANT_ID))) {
                stmt.setString(1, participantId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(builder.build(rs, users));
                }
            } catch (Exception ex) {
                dbVals.resultException = new Exception(String.format("Error getting kits with participant id %s", participantId));
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DsmInternalError(simpleResult.resultException);
        }
        return list;
    }

    public List<NonPepperKitStatusDto> getKitsByKitIdArray(String[] kitIdsArray, Map<Integer, UserDto> users) {
        List<NonPepperKitStatusDto> list = new ArrayList<>();
        for (String kitId : kitIdsArray) {
            List<NonPepperKitStatusDto> listForKitId = this.getKitsByJuniperKitId(kitId, users);
            list.addAll(listForKitId);
        }
        return list;
    }

    private static class BuildNonPepperKitStatusDto {
        public NonPepperKitStatusDto build(ResultSet foundKitResults, Map<Integer, UserDto> users) throws DsmInternalError {
            try {
                return new NonPepperKitStatusDto.Builder()
                        .withJuniperKitId(foundKitResults.getString(DBConstants.DDP_KIT_REQUEST_ID))
                        .withDsmShippingLabel(foundKitResults.getString(DBConstants.DSM_LABEL))
                        .withParticipantId(foundKitResults.getString(DBConstants.DDP_PARTICIPANT_ID))
                        .withReceiveBy(foundKitResults.getString(DBConstants.RECEIVE_BY))
                        .withDeactivationReason(foundKitResults.getString(DBConstants.DEACTIVATION_REASON))
                        .withTrackingNumber(foundKitResults.getString(DBConstants.TRACKING_TO_ID))
                        .withReturnTrackingNumber(foundKitResults.getString(DBConstants.RETURN_TRACKING_NUMBER))
                        .withError(foundKitResults.getBoolean(DBConstants.ERROR))
                        .withErrorMessage(foundKitResults.getString(DBConstants.ERROR_MESSAGE))
                        .withLabelDate(
                                NonPepperStatusKitService.convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.LABEL_DATE)))
                        .withLabelByEmail(
                                NonPepperStatusKitService.getUserEmailForFields(foundKitResults.getString(DBConstants.LABEL_BY), users))
                        .withScanDate(
                                NonPepperStatusKitService.convertTimeStringIntoTimeStamp(
                                        foundKitResults.getLong(DBConstants.DSM_SCAN_DATE)))
                        .withScanByEmail(
                                NonPepperStatusKitService.getUserEmailForFields(foundKitResults.getString(DBConstants.SCAN_BY), users))
                        .withReceiveDate(
                                NonPepperStatusKitService.convertTimeStringIntoTimeStamp(
                                        foundKitResults.getLong(DBConstants.DSM_RECEIVE_DATE)))
                        .withDeactivationDate(NonPepperStatusKitService.convertTimeStringIntoTimeStamp(
                                foundKitResults.getLong(DBConstants.DSM_DEACTIVATED_DATE)))
                        .withDeactivationByEmail(
                                NonPepperStatusKitService.getUserEmailForFields(foundKitResults.getString(DBConstants.DEACTIVATED_BY),
                                        users))
                        .withTrackingScanBy(
                                NonPepperStatusKitService.getUserEmailForFields(foundKitResults.getString(DBConstants.TRACKING_SCAN_BY),
                                        users))
                        .withDiscardDate(
                                NonPepperStatusKitService.convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.DISCARD_DATE)))
                        .withDiscardBy(
                                NonPepperStatusKitService.getUserEmailForFields(foundKitResults.getString(DBConstants.DISCARD_BY), users))
                        .build();
            } catch (SQLException e) {
                throw new DsmInternalError("Error building the NonPepperKitStatusDto object from resultSet", e);
            }
        }

    }
}
