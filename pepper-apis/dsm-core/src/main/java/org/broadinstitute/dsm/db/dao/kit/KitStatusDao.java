package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class KitStatusDao implements Dao<NonPepperKitStatusDto> {

    private static final String SELECT_KIT_STATUS =
            " SELECT req.*, k.*, discard.*, tracking.tracking_id as return_tracking_number, tracking.scan_by as tracking_scan_by, "
                    + " tracking.scan_date as tracking_scan_date, bsp_collaborator_sample_id, bsp_collaborator_participant_id "
                    + " FROM ddp_kit_request req "
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
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new DsmInternalError(String.format("Error getting kits with juniper kit id %s", juniperKitId),
                    simpleResult.resultException);
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
        public static KitCurrentStatus calculateCurrentStatus(ResultSet foundKitResults) {
            try {
                if (isDeactivatedKit(foundKitResults)) {
                    return KitCurrentStatus.DEACTIVATED;
                } else if (isQueuedKit(foundKitResults)) {
                    return KitCurrentStatus.QUEUE;
                } else if (isErrorKit(foundKitResults)) {
                    return KitCurrentStatus.ERROR;
                } else if (isReceivedKit(foundKitResults)) {
                    return KitCurrentStatus.RECEIVED;
                } else if (isSentKit(foundKitResults)) {
                    return KitCurrentStatus.SENT;
                } else if (isNewKit(foundKitResults) || isEasyPostLabelTriggeredKit(foundKitResults)) {
                    return KitCurrentStatus.KIT_WITHOUT_LABEL;
                } else {
                    log.error(String.format("Unable to determine the current status of kit %s",
                            foundKitResults.getString(DBConstants.DDP_KIT_REQUEST_ID)));
                    return null;
                }
            } catch (SQLException e) {
                throw new DsmInternalError(e);
            }
        }

        private static boolean isEasyPostLabelTriggeredKit(ResultSet foundKitResults) throws SQLException {
            return StringUtils.isBlank(foundKitResults.getString(DBConstants.EASYPOST_TO_ID))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.LABEL_URL_TO))
                    && StringUtils.isNotBlank(foundKitResults.getString(DBConstants.LABEL_DATE))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                    "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                    "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)));
        }

        private static boolean isDeactivatedKit(ResultSet foundKitResults) throws SQLException {
            return StringUtils.isNotBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                    && !DBConstants.DEACTIVATION_REASON.equals(foundKitResults.getString(DBConstants.DEACTIVATION_REASON));
        }

        private static boolean isNewKit(ResultSet foundKitResults) throws SQLException {
            return StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                    "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                    !"1".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.LABEL_URL_TO)));
        }

        private static boolean isReceivedKit(ResultSet foundKitResults) throws SQLException {
            return StringUtils.isNotBlank(foundKitResults.getString(DBConstants.DSM_RECEIVE_DATE))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));
        }

        private static boolean isSentKit(ResultSet foundKitResults) throws SQLException {
//        and kit.kit_complete = 1 and kit.deactivated_date is null
            return ("1".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_RECEIVE_DATE))
                    && StringUtils.isNotBlank(foundKitResults.getString(DBConstants.DSM_SCAN_DATE));
        }

        private static boolean isErrorKit(ResultSet foundKitResults) throws SQLException {
            return (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                    "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                    && ("1".equals(foundKitResults.getString(DBConstants.ERROR)))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));
        }

        private static boolean isQueuedKit(ResultSet foundKitResults) throws SQLException {
            return (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                    "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                    && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                    "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                    && StringUtils.isNotBlank(foundKitResults.getString(DBConstants.LABEL_URL_TO))
                    && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));

        }

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
                        .withMfBarcode(foundKitResults.getString(DBConstants.KIT_LABEL))
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
                        .withCurrentStatus(calculateCurrentStatus(foundKitResults).getValue())
                        .withCollaboratorParticipantId(foundKitResults.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID))
                        .withCollaboratorSampleId(foundKitResults.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID))
                        .build();
            } catch (SQLException e) {
                throw new DsmInternalError("Error building the NonPepperKitStatusDto object from resultSet", e);
            }
        }

    }
}
