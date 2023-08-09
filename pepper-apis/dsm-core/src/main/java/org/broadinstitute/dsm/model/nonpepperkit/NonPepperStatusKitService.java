package org.broadinstitute.dsm.model.nonpepperkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.kit.KitStatusDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;

@Slf4j
public class NonPepperStatusKitService {
    private KitStatusDao kitStatusDao;

    public NonPepperStatusKitService() {
        this.kitStatusDao = new KitStatusDao();
    }

    public static String getUserEmailForFields(String userIdInDB, HashMap<Integer, UserDto> users) {
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

    public KitResponse getKitsBasedOnStudyName(String studyGuid) {
        if (StringUtils.isBlank(studyGuid)) {
            return new KitResponseError(KitResponse.UsualErrorMessage.MISSING_STUDY_GUID.getMessage());
        }
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, "juniper_study");
        if (ddpInstance == null || !ddpInstance.isHasRole()) {
            log.error(studyGuid + " is not a Juniper study!");
            return new KitResponseError(KitResponse.UsualErrorMessage.UNKNOWN_STUDY.getMessage());
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get all the kits
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByInstanceId(ddpInstance, users);
        StatusKitResponse statusKitResponse = new StatusKitResponse(list);
        return statusKitResponse;

    }

    public KitResponse getKitsBasedOnJuniperKitId(String juniperKitId) {
        if (StringUtils.isBlank(juniperKitId)) {
            return new KitResponseError(KitResponse.UsualErrorMessage.MISSING_JUNIPER_KIT_ID.getMessage());
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that juniperKitId
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByJuniperKitId(juniperKitId, users);
        StatusKitResponse statusKitResponse = new StatusKitResponse(list);
        return statusKitResponse;

    }

    public KitResponse getKitsBasedOnParticipantId(String participantId) {
        if (StringUtils.isBlank(participantId)) {
            return new KitResponseError(KitResponse.UsualErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID.getMessage());
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that participantId
        ArrayList<NonPepperKitStatusDto> list =
                (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByParticipantId(participantId, users);
        StatusKitResponse statusKitResponse = new StatusKitResponse(list);
        return statusKitResponse;
    }

    public KitResponse getKitsFromKitIds(String[] kitIdsArray) {
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kits with the given kit ids
        try {
            ArrayList<NonPepperKitStatusDto> list = kitStatusDao.getKitsByKitIdArray(kitIdsArray,  users);
            StatusKitResponse statusKitResponse = new StatusKitResponse(list);
            return statusKitResponse;

        } catch (Exception e) {
            log.error("Error getting kits by an array of kit ids", e);
            return new KitResponseError(KitResponse.UsualErrorMessage.DSM_ERROR_SOMETHING_WENT_WRONG.getMessage());
        }
    }

}
