package org.broadinstitute.dsm.model.nonpepperkit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.kit.KitStatusDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;

/**
 * This service returns a Juniper kit's status
 */
@Slf4j
public class NonPepperStatusKitService {
    private KitStatusDao kitStatusDao;
    private static final String QUEUE = "Queue";
    private static final String ERROR = "Error";
    private static final String SENT = "Sent";
    private static final String RECEIVED = "Received";
    private static final String KIT_WITHOUT_LABEL = "Kit Without Label";
    private static final String DEACTIVATED = "Deactivated";
    private static final String KIT_WITHOUT_LABEL_PENDING_LABEL = "Kit Without Label - Label Pending";

    public NonPepperStatusKitService() {
        this.kitStatusDao = new KitStatusDao();
    }

    public static String getUserEmailForFields(String userIdInDB, Map<Integer, UserDto> users) {
        String userEmail = "";
        if (StringUtils.isBlank(userIdInDB)) {
            return userEmail;
        }
        try {
            Integer userId = Integer.parseInt(userIdInDB);
            userEmail = users.containsKey(userId) ? users.get(userId).getEmail().orElse("DSM User") : "DSM User";
        } catch (NumberFormatException e) {
            log.info(String.format(" User Id %s is not a valid integer, returning it as is. ", userIdInDB));
            return userIdInDB;
        }
        return userEmail;
    }

    public static String convertTimeStringIntoTimeStamp(Long dateMillis) {
        if (dateMillis == null || dateMillis == 0L) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(dateMillis);
        return instant.toString();
    }

    public static String calculateCurrentStatus(ResultSet foundKitResults) {
        try {
            if (isQueuedKit(foundKitResults)) {
                return QUEUE;
            } else if (isErrorKit(foundKitResults)) {
                return ERROR;
            } else if (isSentKit(foundKitResults)) {
                return SENT;
            } else if (isReceivedKit(foundKitResults)) {
                return RECEIVED;
            } else if (isNewKit(foundKitResults)) {
                return KIT_WITHOUT_LABEL;
            } else if (isDeactivatedKit(foundKitResults)){
                return DEACTIVATED;
            } else if (isEasyPostLabelTriggeredKit(foundKitResults)){
                return KIT_WITHOUT_LABEL_PENDING_LABEL;
            } else {
                log.error(String.format("Unable to decide the current status of kit %s", foundKitResults.getString(DBConstants.DDP_KIT_REQUEST_ID)));
                return null;
            }
        } catch (SQLException e) {
            throw new DsmInternalError(e);
        }
    }

    private static boolean isEasyPostLabelTriggeredKit(ResultSet foundKitResults) throws SQLException {
//        and kit.easypost_to_id is null and kit.deactivated_date is null and kit.label_date is not null and not (kit.error <=> 1) "
//                    + "and not (kit.kit_complete <=> 1)
        return StringUtils.isBlank(foundKitResults.getString(DBConstants.EASYPOST_TO_ID))
                && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                && StringUtils.isNotBlank(foundKitResults.getString(DBConstants.LABEL_DATE))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)));
    }

    private static boolean isDeactivatedKit(ResultSet foundKitResults) throws SQLException{
//        and kit.deactivated_date is not null
        return StringUtils.isNotBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
            && !DBConstants.DEACTIVATION_REASON.equals(foundKitResults.getString(DBConstants.DEACTIVATION_REASON));
    }

    private static boolean isNewKit(ResultSet foundKitResults) throws SQLException{
//        and kit.easypost_to_id is null and kit.deactivated_date is null and not (kit.error <=> 1) and not (kit.kit_complete <=> 1) "
        return StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                !"1".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.LABEL_URL_TO)));
    }

    private static boolean isReceivedKit(ResultSet foundKitResults) throws SQLException {
//        and kit.receive_date is not null and kit.deactivated_date is null
        return StringUtils.isNotBlank(foundKitResults.getString(DBConstants.DSM_RECEIVE_DATE))
                && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));
    }

    private static boolean isSentKit(ResultSet foundKitResults) throws SQLException{
//        and kit.kit_complete = 1 and kit.deactivated_date is null
        return ("1".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));
    }

    private static boolean isErrorKit(ResultSet foundKitResults) throws SQLException {
//        and not (kit.kit_complete <=> 1) and kit.error = 1 and kit.deactivated_date is null
        return (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                && ("1".equals(foundKitResults.getString(DBConstants.ERROR)))
                && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));
    }

    private static boolean isQueuedKit(ResultSet foundKitResults) throws SQLException {
//        and not (kit.kit_complete <=> 1) and not (kit.error <=> 1) and kit.label_url_to is not null and kit.deactivated_date is null
        return (StringUtils.isBlank(foundKitResults.getString(DBConstants.KIT_COMPLETE)) ||
                "0".equals(foundKitResults.getString(DBConstants.KIT_COMPLETE)))
                && (StringUtils.isBlank(foundKitResults.getString(DBConstants.ERROR)) ||
                "0".equals(foundKitResults.getString(DBConstants.ERROR)))
                && StringUtils.isNotBlank(foundKitResults.getString(DBConstants.LABEL_URL_TO))
                && StringUtils.isBlank(foundKitResults.getString(DBConstants.DSM_DEACTIVATED_DATE));

    }

    public KitResponse getKitsByStudyName(String studyGuid) {
        if (StringUtils.isBlank(studyGuid)) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.MISSING_STUDY_GUID);
        }
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
        if (ddpInstance == null || !ddpInstance.isHasRole()) {
            log.info(studyGuid + " is not a Juniper study!");
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.UNKNOWN_STUDY);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get all the kits
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByInstanceId(ddpInstance, users);
        return KitResponse.makeKitStatusResponse(list);

    }

    public KitResponse getKitsBasedOnJuniperKitId(String juniperKitId) {
        if (StringUtils.isBlank(juniperKitId)) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that juniperKitId
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByJuniperKitId(juniperKitId, users);
        return KitResponse.makeKitStatusResponse(list);

    }

    public KitResponse getKitsBasedOnParticipantId(String participantId) {
        if (StringUtils.isBlank(participantId)) {
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that participantId
        ArrayList<NonPepperKitStatusDto> list =
                (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByParticipantId(participantId, users);
        return KitResponse.makeKitStatusResponse(list);
    }

    public KitResponse getKitsFromKitIds(String[] kitIdsArray) {
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kits with the given kit ids
        try {
            List<NonPepperKitStatusDto> list = kitStatusDao.getKitsByKitIdArray(kitIdsArray, users);
            return KitResponse.makeKitStatusResponse(list);

        } catch (Exception e) {
            log.error("Error getting kits by an array of kit ids", e);
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.DSM_ERROR_SOMETHING_WENT_WRONG);
        }
    }

}
