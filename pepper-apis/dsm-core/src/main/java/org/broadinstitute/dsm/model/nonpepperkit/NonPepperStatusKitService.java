package org.broadinstitute.dsm.model.nonpepperkit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.statics.DBConstants;

@Slf4j
public class NonPepperStatusKitService {
    private static final String STATUS_KIT_THRESHOLD = "STATUS_KIT_THRESHOLD";
    private KitDao kitDao;

    public NonPepperStatusKitService() {
        this.kitDao = new KitDaoImpl();
    }

    public KitResponse getKitsBasedOnStudyName(String studyGuid) {
        if (StringUtils.isBlank(studyGuid)) {
            return new KitResponseError(KitResponse.MISSING_STUDY_GUID);
        }
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, "juniper_study");
        if (ddpInstance == null || !ddpInstance.isHasRole()) {
            log.error(studyGuid + " is not a Juniper study!");
            return new KitResponseError(KitResponse.UNKNOWN_STUDY);
        }
        BookmarkDto kitPagingThreshold = new BookmarkDao().getBookmarkByInstance(STATUS_KIT_THRESHOLD)
                .orElse(new BookmarkDto.Builder(20, STATUS_KIT_THRESHOLD).build()); // use a threshold of 20 if none provided

        // get all the kits
        ResultSet foundKitResults = kitDao.getKitsInDatabaseByInstanceId(ddpInstance);
        return processNonPepperKitStatusFromResultSet(foundKitResults);
    }

    public KitResponse getKitsBasedOnJuniperKitId(String juniperKitId) {
        if (StringUtils.isBlank(juniperKitId)) {
            return new KitResponseError(KitResponse.MISSING_JUNIPER_KIT_ID);
        }
        // get the kit with that juniperKitId
        ResultSet foundKitResults = kitDao.getKitsByJuniperKitId(juniperKitId);
        return processNonPepperKitStatusFromResultSet(foundKitResults);

    }

    public KitResponse getKitsBasedOnParticipantId(String participantId) {
        if (StringUtils.isBlank(participantId)) {
            return new KitResponseError(KitResponse.MISSING_JUNIPER_PARTICIPANT_ID);
        }
        // get the kit with that participantId
        ResultSet foundKitResults = kitDao.getKitsByParticipantId(participantId);
        return processNonPepperKitStatusFromResultSet(foundKitResults);
    }

    private KitResponse processNonPepperKitStatusFromResultSet(ResultSet foundKitResults) {
        ArrayList<NonPepperKitStatus> list = selectAllNonPepperKitStatus(foundKitResults);
        StatusKitResponse statusKitResponse = new StatusKitResponse(list);
        return statusKitResponse;
    }

    private ArrayList<NonPepperKitStatus> selectAllNonPepperKitStatus(ResultSet foundKitResults) {
        HashMap<Integer, UserDto> users = UserDao.selectAllUsers();
        ArrayList<NonPepperKitStatus> kits = new ArrayList<>();
        try {
            while (foundKitResults.next()) {
                kits.add(createStatusForKit(foundKitResults, users));
            }

        } catch (SQLException e) {
            log.error("Some Error getting the status of kits", e);
        }
        return kits;
    }

    private NonPepperKitStatus createStatusForKit(ResultSet foundKitResults, HashMap<Integer, UserDto> users) throws SQLException {
        String labelUser = getUserEmailForFields(foundKitResults.getString(DBConstants.LABEL_BY), users);
        String scannedUser = getUserEmailForFields(foundKitResults.getString(DBConstants.SCAN_BY), users);
        String deactivatedUser = getUserEmailForFields(foundKitResults.getString(DBConstants.DEACTIVATED_BY), users);
        String discardUser = getUserEmailForFields(foundKitResults.getString(DBConstants.DISCARD_BY), users);
        String trackingScanBy = getUserEmailForFields(foundKitResults.getString(DBConstants.TRACKING_SCAN_BY), users);

        String labelDate = convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.LABEL_DATE));
        String scanDate = convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.DSM_SCAN_DATE));
        String deactivatedDate = convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.DSM_DEACTIVATED_DATE));
        String receivedDate = convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.DSM_RECEIVE_DATE));
        String discardDate = convertTimeStringIntoTimeStamp(foundKitResults.getLong(DBConstants.DISCARD_DATE));

        NonPepperKitStatus nonPepperKitStatus =
                new NonPepperKitStatus(foundKitResults.getString(DBConstants.DDP_KIT_REQUEST_ID),
                        foundKitResults.getString(DBConstants.DSM_LABEL),
                        foundKitResults.getString(DBConstants.DDP_PARTICIPANT_ID), labelDate,
                        labelUser, scanDate, scannedUser, receivedDate, foundKitResults.getString(DBConstants.RECEIVE_BY), deactivatedDate,
                        deactivatedUser, foundKitResults.getString(DBConstants.DEACTIVATION_REASON),
                        foundKitResults.getString(DBConstants.TRACKING_TO_ID),
                        foundKitResults.getString(DBConstants.RETURN_TRACKING_NUMBER), trackingScanBy,
                        foundKitResults.getBoolean(DBConstants.ERROR), foundKitResults.getString(DBConstants.ERROR_MESSAGE),
                        discardDate, discardUser);

        return nonPepperKitStatus;

    }

    public String getUserEmailForFields(String userIdInDB, HashMap<Integer, UserDto> users) {
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

    public String convertTimeStringIntoTimeStamp(Long dateMillis) {
        Instant instant = Instant.ofEpochMilli(dateMillis);
        return instant.toString();
    }
}
