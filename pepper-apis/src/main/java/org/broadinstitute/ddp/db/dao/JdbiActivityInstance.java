package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable.GUID;
import static org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable.TABLE_NAME;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusChangeDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiActivityInstance extends SqlObject {

    default String generateUniqueGuid() {
        return DBUtils.uniqueStandardGuid(getHandle(), TABLE_NAME, GUID);
    }

    @SqlQuery("SELECT"
            + " ais.updated_at as updated_at_epoch_millis, ais.activity_instance_id, aist.activity_instance_status_type_code "
            + " as activity_instance_status_type "
            + " FROM activity_instance_status ais "
            + " JOIN activity_instance_status_type aist on ais.activity_instance_status_type_id = aist.activity_instance_status_type_id "
            + " JOIN activity_instance ai on ais.activity_instance_id = ai.activity_instance_id "
            + " JOIN study_activity sa on ai.study_activity_id = sa.study_activity_id "
            + " WHERE sa.study_activity_id = :activityId "
            + "     and ai.participant_id = :participantId "
            + "     and aist.activity_instance_status_type_code in (<activityStatusTypeCodes>) "
            + " ORDER BY ais.updated_at")
    @RegisterConstructorMapper(ActivityInstanceStatusChangeDto.class)
    List<ActivityInstanceStatusChangeDto> getActivityInstanceStatusChanges(
            @Bind("activityId") long activityId,
            @Bind("participantId") long participantId,
            @BindList("activityStatusTypeCodes") InstanceStatusType...statusType);

    @SqlQuery("select activity_instance_id from activity_instance where "
            + "activity_instance_guid = :activityInstanceGuid")
    long getActivityInstanceId(String activityInstanceGuid);

    @SqlQuery("select activity_instance_guid from activity_instance where "
            + "activity_instance_id = :activityInstanceId")
    String getActivityInstanceGuid(@Bind("activityInstanceId") long activityInstanceId);

    @SqlUpdate("insert into activity_instance (study_activity_id,participant_id,activity_instance_guid,is_readonly,"
            + "created_at,ondemand_trigger_id) values(:activityId,:participantId,:guid,:isReadOnly,:createdAt,:triggerId)")
    @GetGeneratedKeys
    long insert(@Bind("activityId") long activityId,
                @Bind("participantId") long participantId,
                @Bind("guid") String instanceGuid,
                @Bind("isReadOnly") Boolean isReadOnly,
                @Bind("createdAt") long createdAtMillis,
                @Bind("triggerId") Long onDemandTriggerId);

    @SqlUpdate("insert into activity_instance (study_activity_id,participant_id,activity_instance_guid,is_readonly,"
            + "created_at, legacy_submissionid,legacy_sessionid,legacy_version) values(:activityId,:participantId,:guid,"
            + ":isReadOnly,:createdAt,:submissionId,:sessionId,:legacyVersion)")
    @GetGeneratedKeys
    long insertLegacyInstance(@Bind("activityId") long activityId,
                              @Bind("participantId") long participantId,
                              @Bind("guid") String activityInstanceGuid,
                              @Bind("isReadOnly") Boolean isReadOnly,
                              @Bind("createdAt") long createdAtMillis,
                              @Bind("submissionId") Long submissionId,
                              @Bind("sessionId") String sessionId,
                              @Bind("legacyVersion") String legacyVersion);

    @SqlUpdate("delete from activity_instance where activity_instance_id = :id")
    int delete(@Bind long id);

    @SqlQuery("queryById")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(ActivityInstanceDto.class)
    Optional<ActivityInstanceDto> getByActivityInstanceId(@Bind("activityInstanceId") long activityInstanceId);

    @SqlQuery("queryByGuid")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(ActivityInstanceDto.class)
    Optional<ActivityInstanceDto> getByActivityInstanceGuid(@Bind("activityInstanceGuid") String activityInstanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryByUserGuidAndInstanceGuid")
    @RegisterConstructorMapper(ActivityInstanceDto.class)
    Optional<ActivityInstanceDto> getByUserAndInstanceGuids(@Bind("userGuid") String userGuid,
                                                            @Bind("instanceGuid") String instanceGuid);

    @SqlUpdate("update activity_instance set is_readonly = :isReadonly where activity_instance_guid "
            + "= :activityInstanceGuid")
    int updateIsReadonlyByGuid(Boolean isReadonly, String activityInstanceGuid);

    @SqlUpdate("update activity_instance set first_completed_at = :timestamp"
            + " where activity_instance_id = :id and first_completed_at is null")
    int updateFirstCompletedAtIfNotSet(@Bind("id") long instanceId, @Bind("timestamp") long firstCompletedAtMillis);

    @SqlQuery(
            "SELECT ai.study_activity_id FROM activity_instance AS ai"
            + " WHERE ai.activity_instance_guid = :guid"
    )
    long getActivityIdByGuid(@Bind("guid") String guid);

    // Note: this should only be used in tests.
    @SqlUpdate("delete from activity_instance where activity_instance_guid = ?")
    int deleteByInstanceGuid(String instanceGuid);

    @SqlQuery(
            "select count(*) from activity_instance where study_activity_id = :studyActivityId"
                    + " and participant_id = :participantId"
    )
    int getNumActivitiesForParticipant(
            @Bind("studyActivityId") long studyActivityId,
            @Bind("participantId") long participantId
    );

    @SqlQuery("select count(*) from activity_instance")
    int getCount();

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllByUserGuidAndActivityCode")
    @RegisterConstructorMapper(ActivityInstanceDto.class)
    List<ActivityInstanceDto> findAllByUserGuidAndActivityCode(@Bind("userGuid") String userGuid,
                                                               @Bind("activityCode") String activityCode,
                                                               @Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllByUserIdAndStudyId")
    @RegisterConstructorMapper(ActivityInstanceDto.class)
    List<ActivityInstanceDto> findAllByUserIdAndStudyId(@Bind("userId") long userId, @Bind("studyId") long studyId);

    @SqlQuery("select ai.activity_instance_guid "
            + " from activity_instance as ai "
            + " join user as u on ai.participant_id = u.user_id"
            + " join study_activity as act on act.study_activity_id = ai.study_activity_id"
            + " join question as q on q.study_activity_id = act.study_activity_id"
            + " where q.question_id = :questionId"
            + " and u.guid = :userGuid"
            + " order by ai.created_at desc limit 1")
    Optional<String> findLatestInstanceGuidFromUserGuidAndQuestionId(@Bind("userGuid") String userGuid,
                                                                     @Bind("questionId") long questionId);

    @SqlQuery(
            "select ai.activity_instance_id from user u join activity_instance ai on ai.participant_id = u.user_id"
                    + " join study_activity su on ai.study_activity_id = su.study_activity_id join umbrella_study us"
                    + " on su.study_id = us.umbrella_study_id where us.guid = :studyGuid and u.guid = :userGuid"

    )
    List<Long> findsIdByUserGuidAndStudyGuid(@Bind("userGuid") String userGuid, @Bind("studyGuid") String studyGuid);

    @SqlQuery(
            "select ai.is_readonly from user u join activity_instance ai on ai.participant_id = u.user_id"
                    + " join study_activity su on ai.study_activity_id = su.study_activity_id join umbrella_study us"
                    + " on su.study_id = us.umbrella_study_id where us.guid = :studyGuid and u.guid = :userGuid"

    )
    List<Boolean> findIsReadonlyByUserGuidAndStudyGuid(@Bind("userGuid") String userGuid, @Bind("studyGuid") String studyGuid);

    @SqlQuery("select is_readonly from activity_instance where activity_instance_id = :id")
    Optional<Boolean> findIsReadonlyById(@Bind("id") long id);

    @SqlUpdate("update activity_instance set is_readonly = 1 where activity_instance_id in (<activityInstanceIds>)")
    int makeActivityInstancesReadonly(@BindList("activityInstanceIds") List<Long> activityInstanceIds);

    default int makeUserActivityInstancesReadonly(String studyGuid, String userGuid) {
        List<Long> instanceIds = findsIdByUserGuidAndStudyGuid(userGuid, studyGuid);
        if (instanceIds.isEmpty()) {
            return 0;
        }
        int numUpdated = makeActivityInstancesReadonly(instanceIds);
        if (numUpdated != instanceIds.size()) {
            throw new DaoException(
                    "Expected to update " + instanceIds.size()
                            + " activity instances but actually updated " + numUpdated
            );
        }
        return numUpdated;
    }

    @SqlUpdate("update activity_instance set ondemand_trigger_id = :triggerId "
            + " where participant_id = :userId and activity_instance_id = :activityInstanceId")
    int updateOndemandTriggerId(@Bind("userId") long userId, @Bind("activityInstanceId") long activityInstanceId,
                                        @Bind("triggerId") long triggerId);

    @SqlUpdate("update activity_instance set last_visited_section = :lastVisitedActivitySection "
            + " where activity_instance_guid = :instanceGuid")
    void updateLastVisitedActivitySection(@Bind("instanceGuid") String instanceGuid,
                                   @Bind("lastVisitedActivitySection") int lastVisitedActivitySection);

    @SqlQuery("queryLatestGuidByUserGuidAndCodesOfActivities")
    @UseStringTemplateSqlLocator
    Optional<String> findLatestInstanceGuidByUserGuidAndCodesOfActivities(
            @Bind("userGuid") String userGuid,
            @BindList("activityCodes") List<String> activityCodes,
            @Bind("studyId") long studyId
    );

    @SqlQuery("select ai.activity_instance_guid from activity_instance as ai"
            + " where ai.participant_id = :userId and ai.study_activity_id = :activityId"
            + " order by ai.created_at desc limit 1")
    Optional<String> findLatestInstanceGuidByUserIdAndActivityId(@Bind("userId") long userId, @Bind("activityId") long activityId);

    @SqlQuery("select ai.activity_instance_id from activity_instance as ai"
            + "  join user as u on u.user_id = ai.participant_id"
            + " where ai.study_activity_id = :activityId"
            + "   and u.guid = :userGuid"
            + " order by ai.created_at desc limit 1")
    Optional<Long> findLatestInstanceIdByUserGuidAndActivityId(@Bind("userGuid") String userGuid, @Bind("activityId") long activityId);

    @SqlQuery("select ai.activity_instance_guid from activity_instance as ai"
            + "  join user as u on u.user_id = ai.participant_id"
            + " where ai.study_activity_id = :activityId"
            + "   and u.guid = :userGuid"
            + " order by ai.created_at desc limit 1")
    Optional<String> findLatestInstanceGuidByUserGuidAndActivityId(@Bind("userGuid") String userGuid, @Bind("activityId") long activityId);
}
