package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Database object for changing enrollment statuses. Do not use direct inserts and updates, rather use
 * convenience functions {@link #changeUserStudyEnrollmentStatus} and {@link #terminateStudyEnrollment}
 */
public interface JdbiUserStudyEnrollment extends SqlObject {

    @CreateSqlObject
    JdbiUser getJdbiUser();

    @CreateSqlObject
    JdbiUmbrellaStudy getJdbiUmbrellaStudy();

    @CreateSqlObject
    ActivityInstanceDao getActivityInstanceDao();

    default List<EnrollmentStatusDto> findByStudyGuid(String studyGuid) {
        return findByStudyGuidAfterOrEqualToInstant(studyGuid, 0);
    }

    @SqlQuery("SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id, us.guid as study_guid, "
            + " est.enrollment_status_type_code as enrollment_status, usen.valid_from as valid_from_millis,"
            + " usen.valid_to as valid_to_millis"
            + " FROM user_study_enrollment usen "
            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
            + " JOIN user ON usen.user_id = user.user_id "
            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
            + " WHERE "
            + " us.guid = :studyGuid "
            + " AND valid_from >= :instant"
            + " AND valid_to is null"
    )
    @RegisterConstructorMapper(EnrollmentStatusDto.class)
    List<EnrollmentStatusDto> findByStudyGuidAfterOrEqualToInstant(@Bind("studyGuid") String studyGuid,
                                                                   @Bind("instant") long instantMillis);

    default List<EnrollmentStatusDto> findByUserGuid(String userGuid) {
        return findByUserGuidAfterOrEqualToInstant(userGuid, 0);
    }

    @SqlQuery("SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id, us.guid as study_guid, "
            + " est.enrollment_status_type_code as enrollment_status, usen.valid_from as valid_from_millis,"
            + " usen.valid_to as valid_to_millis"
            + " FROM user_study_enrollment usen "
            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
            + " JOIN user ON usen.user_id = user.user_id "
            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
            + " WHERE "
            + " user.guid = :userGuid "
            + " AND valid_from >= :instant"
            + " AND valid_to is null"
    )
    @RegisterConstructorMapper(EnrollmentStatusDto.class)
    List<EnrollmentStatusDto> findByUserGuidAfterOrEqualToInstant(@Bind("userGuid") String userGuid,
                                                                  @Bind("instant") long instantMillis);

    @SqlQuery("SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id, us.guid as study_guid, "
            + " est.enrollment_status_type_code as enrollment_status, usen.valid_from as valid_from_millis,"
            + " usen.valid_to as valid_to_millis"
            + " FROM user_study_enrollment usen "
            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
            + " JOIN user ON usen.user_id = user.user_id "
            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
            + " WHERE "
            + " user.user_id in (<userIds>)"
            + " AND valid_to is null"
    )
    @RegisterConstructorMapper(EnrollmentStatusDto.class)
    Stream<EnrollmentStatusDto> findAllLatestByUserIds(
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds);

    @SqlQuery("SELECT ma.pluscode"
            + " FROM user_study_enrollment usen "
            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
            + " JOIN mailing_address ma ON ma.participant_user_id = usen.user_id"
            + " JOIN default_mailing_address dma ON dma.address_id = ma.address_id"
            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
            + " WHERE "
            + " us.guid = :studyGuid "
            + " AND est.enrollment_status_type_code = 'ENROLLED'"
            + " AND usen.valid_to is null"
    )
    List<String> findAllOLCsForEnrolledParticipantsInStudy(@Bind("studyGuid") String studyGuid);

    @SqlQuery(
            "SELECT user_study_enrollment_id FROM user_study_enrollment"
                    + " WHERE user_id = (select user_id from user where guid = :userGuid)"
                    + " AND study_id = (select umbrella_study_id from umbrella_study where guid = :studyGuid)"
                    + " AND valid_to is null"
    )
    Optional<Long> findIdByUserAndStudyGuid(@Bind("userGuid") String userGuid, @Bind("studyGuid") String studyGuid);

    @SqlUpdate(
            "insert into user_study_enrollment (user_id, study_id, enrollment_status_type_id, valid_from)"
                    + " values ((select user_id from user where guid = :userGuid),"
                    + " (select umbrella_study_id from umbrella_study where guid = :studyGuid),"
                    + " (select enrollment_status_type_id from enrollment_status_type"
                    + " where enrollment_status_type_code = :enrollmentStatusTypeCode), :validFrom)"
    )
    @GetGeneratedKeys
    long insert(
            @Bind("userGuid") String userGuid,
            @Bind("studyGuid") String studyGuid,
            @Bind("enrollmentStatusTypeCode") EnrollmentStatusType enrollmentStatusTypeCode,
            @Bind("validFrom") long lastUpdated
    );

    @SqlUpdate(
            "update user_study_enrollment set valid_to = :validTo"
                    + " where user_id = (select user_id from user where guid = :userGuid)"
                    + " and study_id = (select umbrella_study_id from umbrella_study where guid = :studyGuid)"
                    + " and valid_to is null"
    )
    int updateStatusByUserAndStudyGuids(
            @Bind("userGuid") String userGuid,
            @Bind("studyGuid") String studyGuid,
            @Bind("validTo") long validTo
    );


    @SqlUpdate("delete from user_study_enrollment where user_study_enrollment_id = :id")
    int deleteById(@Bind("id") long id);

    @SqlUpdate(
            "delete from user_study_enrollment where "
                    + " user_id =  (select user_id from user where guid = :userGuid)"
                    + " and study_id = (select umbrella_study_id from umbrella_study where guid = :studyGuid)"
    )
    int deleteByUserGuidStudyGuid(@Bind("userGuid") String userGuid,
                                  @Bind("studyGuid") String studyGuid);

    @SqlQuery(
            "select est.enrollment_status_type_code from user_study_enrollment uste, enrollment_status_type est where"
                    + " uste.enrollment_status_type_id = est.enrollment_status_type_id"
                    + " and user_id = (select user_id from user where guid = :userGuid)"
                    + " and study_id = (select umbrella_study_id from umbrella_study where guid = :studyGuid)"
                    + " AND valid_to is null"
    )
    Optional<EnrollmentStatusType> getEnrollmentStatusByUserAndStudyGuids(
            @Bind("userGuid") String userGuid,
            @Bind("studyGuid") String studyGuid
    );

    @SqlQuery("SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id, us.guid as study_guid, "
            + " est.enrollment_status_type_code as enrollment_status, usen.valid_from as valid_from_millis,"
            + " usen.valid_to as valid_to_millis"
            + " FROM user_study_enrollment usen "
            + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
            + " JOIN user ON usen.user_id = user.user_id "
            + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
            + " WHERE "
            + " user.guid = :userGuid "
            + " AND valid_to is null"
    )
    @RegisterConstructorMapper(EnrollmentStatusDto.class)
    List<EnrollmentStatusDto> getAllLatestEnrollmentsForUser(@Bind("userGuid") String userGuid);

    @SqlQuery(
            "select est.enrollment_status_type_code from user_study_enrollment uste, enrollment_status_type est"
                    + " where uste.enrollment_status_type_id = est.enrollment_status_type_id"
                    + " and user_id = :userId"
                    + " and study_id = :studyId"
                    + " and valid_to is null"
    )
    Optional<EnrollmentStatusType> getEnrollmentStatusByUserAndStudyIds(
            @Bind("userId") long userId,
            @Bind("studyId") long studyId
    );

    @SqlQuery(
            "SELECT usen.user_study_enrollment_id, usen.user_id, user.guid as user_guid, usen.study_id, us.guid as study_guid, "
                    + " est.enrollment_status_type_code as enrollment_status, usen.valid_from as valid_from_millis,"
                    + " usen.valid_to as valid_to_millis"
                    + " FROM user_study_enrollment usen "
                    + " JOIN enrollment_status_type est on usen.enrollment_status_type_id = est.enrollment_status_type_id "
                    + " JOIN user ON usen.user_id = user.user_id "
                    + " JOIN umbrella_study us ON us.umbrella_study_id = usen.study_id "
                    + " AND usen.user_id = :userId"
                    + " AND usen.study_id = :studyId"
    )
    @RegisterConstructorMapper(EnrollmentStatusDto.class)
    List<EnrollmentStatusDto> getAllEnrollmentStatusesByUserAndStudyIds(
            @Bind("userId") long userId,
            @Bind("studyId") long studyId
    );

    /**
     * Update a user's enrollment status if they already have one, or insert a new status for them if not.
     *
     * @param userGuid                the user guid
     * @param studyGuid               the study guid
     * @param newEnrollmentStatus     the desired enrollment status for the user
     * @param currentEnrollmentStatus the user's current status
     * @param updateTime              the time that we want to record the status was updated at
     * @return the id of the new enrollment status
     */
    private long changeUserStudyEnrollmentStatus(
            String userGuid,
            String studyGuid,
            EnrollmentStatusType newEnrollmentStatus,
            EnrollmentStatusType currentEnrollmentStatus,
            long updateTime
    ) {
        // Create if not exists
        if (currentEnrollmentStatus != null) {
            int numUpdated = updateStatusByUserAndStudyGuids(userGuid, studyGuid, updateTime);
            if (numUpdated != 1) {
                throw new DaoException("Expected to update study enrollment status for 1 user, but updated "
                        + numUpdated + " records");
            }
        }
        return insert(userGuid, studyGuid, newEnrollmentStatus, updateTime);
    }

    /**
     * Function to be used by outside of this class to update change user study enrollment status.
     * For a given user and study, check if they have a current enrollment status, use it if so,
     * and then call the internal update function and defaulting the timestamp to the current time
     * if one is not given.
     *
     * @param userGuid            the user guid
     * @param studyGuid           the study guid
     * @param newEnrollmentStatus the desired status to update the user to
     * @param updateTime          not required parameter, indicates when previous status (if the user had one)
     *                            is valid to and when the new status is valid to
     * @return the id of the new enrollment status
     */
    default long changeUserStudyEnrollmentStatus(String userGuid,
                                                 String studyGuid,
                                                 EnrollmentStatusType newEnrollmentStatus,
                                                 Long updateTime) {
        Optional<EnrollmentStatusType> optionalCurrentEnrollmentStatus =
                getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid);
        // Default to now if does not exist
        if (updateTime == null) {
            updateTime = Instant.now().toEpochMilli();
        }

        EnrollmentStatusType currentEnrollmentStatus = null;
        if (optionalCurrentEnrollmentStatus.isPresent()) {
            currentEnrollmentStatus = optionalCurrentEnrollmentStatus.get();
        }

        return changeUserStudyEnrollmentStatus(userGuid,
                studyGuid,
                newEnrollmentStatus,
                currentEnrollmentStatus,
                updateTime);
    }

    /**
     * Function to be used by outside of this class to update change user study enrollment status.
     * For a given user and study, check if they have a current enrollment status, use it if so,
     * and then call the internal update function and defaulting the timestamp to the current time
     * if one is not given.
     *
     * @param userGuid            the user guid
     * @param studyGuid           the study guid
     * @param newEnrollmentStatus the desired status to update the user to
     * @return the id of the new enrollment status
     */
    default long changeUserStudyEnrollmentStatus(String userGuid,
                                                 String studyGuid,
                                                 EnrollmentStatusType newEnrollmentStatus) {
        return changeUserStudyEnrollmentStatus(userGuid, studyGuid, newEnrollmentStatus, null);
    }

    /**
     * Function to be used by outside of this class to update change user study enrollment status.
     * For a given user and study, check if they have a current enrollment status, use it if so,
     * and then call the internal update function and defaulting the timestamp to the current time
     * if one is not given.
     *
     * @param userId              the user id
     * @param studyId             the study id
     * @param newEnrollmentStatus the desired status to update the user to
     * @return the id of the new enrollment status
     */
    default long changeUserStudyEnrollmentStatus(long userId,
                                                 long studyId,
                                                 EnrollmentStatusType newEnrollmentStatus,
                                                 Long updatedTime) {
        String userGuid = getJdbiUser().findByUserId(userId).getUserGuid();
        String studyGuid = getJdbiUmbrellaStudy().findGuidByStudyId(studyId);
        return changeUserStudyEnrollmentStatus(userGuid, studyGuid, newEnrollmentStatus, updatedTime);
    }

    default long changeUserStudyEnrollmentStatus(long userId,
                                                 long studyId,
                                                 EnrollmentStatusType newEnrollmentStatus) {
        return changeUserStudyEnrollmentStatus(userId, studyId, newEnrollmentStatus, null);
    }

    default long suspendUserStudyConsent(long userId, long studyId) {
        //update EnrollmentStatus to EnrollmentStatusType.CONSENT_SUSPENDED
        //Update All existing activity instances as read-only
        long id = changeUserStudyEnrollmentStatus(
                userId, studyId, EnrollmentStatusType.CONSENT_SUSPENDED, null);
        Set<Long> instanceIds = getActivityInstanceDao().findAllInstanceIdsByUserIdAndStudyId(userId, studyId);
        getActivityInstanceDao().bulkUpdateReadOnlyByActivityIds(userId, true, instanceIds);
        return id;
    }

    /**
     * Special case of changing a user's enrollment status. Sets desired enrollment status depending on
     * whether the user completed enrollment before exiting.
     *
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @param timestamp the time that we want to record the status was updated at
     */
    default void terminateStudyEnrollment(String userGuid, String studyGuid, Long timestamp) {
        Optional<EnrollmentStatusType> optionalCurrentEnrollmentStatus =
                getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid);

        EnrollmentStatusType currentEnrollmentStatus = null;
        if (optionalCurrentEnrollmentStatus.isPresent()) {
            currentEnrollmentStatus = optionalCurrentEnrollmentStatus.get();
        }

        EnrollmentStatusType newEnrollmentStatus = EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT;
        if (currentEnrollmentStatus == EnrollmentStatusType.ENROLLED) {
            newEnrollmentStatus = EnrollmentStatusType.EXITED_AFTER_ENROLLMENT;
        }

        // Default to now if does not exist
        if (timestamp == null) {
            timestamp = Instant.now().toEpochMilli();
        }
        changeUserStudyEnrollmentStatus(userGuid,
                studyGuid,
                newEnrollmentStatus,
                currentEnrollmentStatus,
                timestamp);
    }

    /**
     * Terminate a user's study enrollment that defaults the updatedAt timestamp to current time.
     * See {@link #terminateStudyEnrollment(String, String, Long)} to set time.
     *
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     */
    default void terminateStudyEnrollment(String userGuid, String studyGuid) {
        terminateStudyEnrollment(userGuid, studyGuid, null);
    }
}
