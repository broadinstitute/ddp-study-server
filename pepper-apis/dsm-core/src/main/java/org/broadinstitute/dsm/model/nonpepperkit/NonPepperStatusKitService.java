package org.broadinstitute.dsm.model.nonpepperkit;

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

/**
 * This service returns a Juniper kit's status
 *
 */
@Slf4j
public class NonPepperStatusKitService {
    private KitStatusDao kitStatusDao;

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

    public KitResponse getKitsByStudyName(String studyGuid) {
        if (StringUtils.isBlank(studyGuid)) {
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.MISSING_STUDY_GUID);
        }
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, "juniper_study");
        if (ddpInstance == null || !ddpInstance.isHasRole()) {
            log.info(studyGuid + " is not a Juniper study!");
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.UNKNOWN_STUDY);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get all the kits
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByInstanceId(ddpInstance, users);
        return new KitResponse().makeKitStatusResponse(list);

    }

    public KitResponse getKitsBasedOnJuniperKitId(String juniperKitId) {
        if (StringUtils.isBlank(juniperKitId)) {
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that juniperKitId
        ArrayList<NonPepperKitStatusDto> list = (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByJuniperKitId(juniperKitId, users);
        return new KitResponse().makeKitStatusResponse(list);

    }

    public KitResponse getKitsBasedOnParticipantId(String participantId) {
        if (StringUtils.isBlank(participantId)) {
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_PARTICIPANT_ID);
        }
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kit with that participantId
        ArrayList<NonPepperKitStatusDto> list =
                (ArrayList<NonPepperKitStatusDto>) kitStatusDao.getKitsByParticipantId(participantId, users);
        return  new KitResponse().makeKitStatusResponse(list);
    }

    public KitResponse getKitsFromKitIds(String[] kitIdsArray) {
        HashMap<Integer, UserDto> users = (HashMap<Integer, UserDto>) UserDao.selectAllUsers();
        // get the kits with the given kit ids
        try {
            List<NonPepperKitStatusDto> list = kitStatusDao.getKitsByKitIdArray(kitIdsArray,  users);
            return new KitResponse().makeKitStatusResponse(list);

        } catch (Exception e) {
            log.error("Error getting kits by an array of kit ids", e);
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.DSM_ERROR_SOMETHING_WENT_WRONG);
        }
    }

}
