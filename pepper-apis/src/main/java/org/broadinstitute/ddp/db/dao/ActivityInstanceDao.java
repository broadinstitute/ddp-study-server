package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceCreationValidation;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ActivityInstanceDao extends SqlObject {

    @CreateSqlObject
    JdbiUser getJdbiUser();

    @CreateSqlObject
    JdbiActivityInstance getJdbiActivityInstance();

    @CreateSqlObject
    ActivityInstanceStatusDao getActivityInstanceStatusDao();

    @CreateSqlObject
    AnswerDao getAnswerDao();

    @CreateSqlObject
    ActivityInstanceSql getActivityInstanceSql();

    /**
     * Checks whether or not a new instance of activityCode can be created for userGuid by looking at counts of
     * instances.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryActivityInstanceCreationValidation")
    @RegisterConstructorMapper(ActivityInstanceCreationValidation.class)
    Optional<ActivityInstanceCreationValidation> checkSuitabilityForActivityInstanceCreation(
            @Bind("studyId") long studyId,
            @Bind("activityCode") String activityCode,
            @Bind("userGuid") String userGuid);

    /**
     * Convenience method to create new activity instance when both operator and participant is the same, and using defaults.
     */
    default ActivityInstanceDto insertInstance(long activityId, String userGuid) {
        long millis = Instant.now().toEpochMilli();
        return insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, null, millis);
    }

    /**
     * Convenience method to create new activity instance at the current time.
     */
    default ActivityInstanceDto insertInstance(long activityId,
                                               String operatorGuid,
                                               String participantGuid,
                                               InstanceStatusType initialStatus,
                                               Boolean isReadOnly) {
        long millis = Instant.now().toEpochMilli();
        return insertInstance(activityId, operatorGuid, participantGuid, initialStatus, isReadOnly, millis);
    }

    /**
     * Creates a new activity instance of the given activity for the given participant, on behalf of the given operator. Guid is generated
     * internally.
     *
     * @param activityId      the associated activity
     * @param operatorGuid    the user that created this instance
     * @param participantGuid the user this instance is for
     * @param initialStatus   the starting status
     * @param isReadOnly      whether read only or not
     * @param createdAtMillis the creation timestamp in milliseconds
     * @return newly created activity instance
     */
    default ActivityInstanceDto insertInstance(long activityId, String operatorGuid, String participantGuid,
                                               InstanceStatusType initialStatus, Boolean isReadOnly,
                                               long createdAtMillis) {
        return insertInstance(activityId, operatorGuid, participantGuid, initialStatus, isReadOnly, createdAtMillis, null);
    }

    /**
     * Creates a new activity instance.
     *
     * @param activityId        the associated activity
     * @param operatorGuid      the user that created this instance
     * @param participantGuid   the participant user
     * @param initialStatus     the starting status
     * @param isReadOnly        whether readonly or not
     * @param createdAtMillis   the creation timestamp in milliseconds
     * @param onDemandTriggerId the trigger request id, if from on-demand request
     * @return newly created activity instance
     */
    default ActivityInstanceDto insertInstance(long activityId, String operatorGuid, String participantGuid,
                                               InstanceStatusType initialStatus, Boolean isReadOnly,
                                               long createdAtMillis, Long onDemandTriggerId) {
        JdbiActivityInstance jdbiInstance = getJdbiActivityInstance();
        ActivityInstanceStatusDao statusDao = getActivityInstanceStatusDao();

        String instanceGuid = jdbiInstance.generateUniqueGuid();
        long participantId = getJdbiUser().getUserIdByGuid(participantGuid);
        long instanceId = jdbiInstance.insert(activityId, participantId, instanceGuid, isReadOnly,
                createdAtMillis, onDemandTriggerId);
        statusDao.insertStatus(instanceId, initialStatus, createdAtMillis, operatorGuid);

        return jdbiInstance.getByActivityInstanceId(instanceId).orElseThrow(() ->
                new DaoException("Could not find newly created activity instance with id " + instanceId));
    }

    /**
     * Creates a new activity instance of the given activity for the migration participant, on behalf of the given operator.
     *
     * @param activityId      the associated activity
     * @param operatorGuid    the user that created this instance
     * @param participantGuid the user this instance is for
     * @param initialStatus   the starting status
     * @param isReadOnly      whether read only or not
     * @param createdAtMillis the creation timestamp in milliseconds
     * @param submissionId    submission ID from migrated data
     * @param sessionId       session ID from migrated data
     * @return newly created activity instance
     */
    default ActivityInstanceDto insertInstance(long activityId, String operatorGuid, String participantGuid,
                                               InstanceStatusType initialStatus, Boolean isReadOnly,
                                               long createdAtMillis, Long submissionId,
                                               String sessionId, String legacyVersion) {
        JdbiActivityInstance jdbiInstance = getJdbiActivityInstance();
        ActivityInstanceStatusDao statusDao = getActivityInstanceStatusDao();

        String instanceGuid = jdbiInstance.generateUniqueGuid();
        long participantId = getJdbiUser().getUserIdByGuid(participantGuid);
        long instanceId = jdbiInstance.insertLegacyInstance(activityId, participantId, instanceGuid,
                isReadOnly, createdAtMillis, submissionId, sessionId, legacyVersion);
        statusDao.insertStatus(instanceId, initialStatus, createdAtMillis, operatorGuid);

        return jdbiInstance.getByActivityInstanceId(instanceId).orElseThrow(() ->
                new DaoException("Could not find newly created activity instance with id " + instanceId));
    }

    @SqlUpdate("update activity_instance set is_readonly = :isReadOnly"
            + "  where participant_id = :participantId and study_activity_id in (<activityIds>)")
    int bulkUpdateReadOnlyByActivityIds(
            @Bind("participantId") long participantId,
            @Bind("isReadOnly") boolean isReadOnly,
            @BindList(value = "activityIds", onEmpty = EmptyHandling.NULL) Set<Long> activityIds);

    @SqlUpdate("update activity_instance set is_hidden = :isHidden"
            + "  where participant_id = :participantId and study_activity_id in (<activityIds>)")
    int bulkUpdateIsHiddenByActivityIds(
            @Bind("participantId") long participantId,
            @Bind("isHidden") boolean isHidden,
            @BindList(value = "activityIds", onEmpty = EmptyHandling.NULL) Set<Long> activityIds);

    @SqlUpdate("update activity_instance as ai"
            + "   join study_activity as act on act.study_activity_id = ai.study_activity_id"
            + "    set ai.participant_id = :newParticipantId"
            + "  where ai.participant_id = :oldParticipantId"
            + "    and act.study_id = :studyId")
    int reassignInstancesInStudy(
            @Bind("studyId") long studyId,
            @Bind("oldParticipantId") long oldParticipantId,
            @Bind("newParticipantId") long newParticipantId);

    @SqlQuery("select activity_instance_id from activity_instance where participant_id in (<userIds>)")
    Set<Long> findAllInstanceIdsByUserIds(@BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds);

    @SqlQuery("select ai.activity_instance_id from activity_instance as ai "
            + " join study_activity as sa on sa.study_activity_id = ai.study_activity_id"
            + " where ai.participant_id = :userId and sa.study_id = :studyId")
    Set<Long> findAllInstanceIdsByUserIdAndStudyId(@Bind("userId") long userId, @Bind("studyId") long studyId);

    @SqlQuery("select activity_instance_id"
            + "  from activity_instance as ai"
            + "  join (select participant_id, study_activity_id, created_at"
            + "          from activity_instance where activity_instance_id = :instanceId)"
            + "       as ai2 on ai.participant_id = ai2.participant_id"
            + "       and ai.study_activity_id = ai2.study_activity_id"
            + "       and ai.created_at <= ai2.created_at"
            + " where ai.activity_instance_id != :instanceId"
            + " order by ai.created_at desc"
            + " limit 1")
    Optional<Long> findMostRecentInstanceBeforeCurrent(@Bind("instanceId") long currentInstanceId);

    @SqlQuery("select count(ai.activity_instance_id)"
            + "  from activity_instance as ai"
            + "  join (select rev.start_date, rev.end_date"
            + "          from activity_version as ver"
            + "          join revision as rev on rev.revision_id = ver.revision_id"
            + "         where ver.study_activity_id = :activityId and ver.activity_version_id = :versionId"
            + "       ) as ver on ver.start_date <= ai.created_at"
            + "       and (ver.end_date is null or ai.created_at < ver.end_date)"
            + " where ai.study_activity_id = :activityId"
            + " group by ai.participant_id"
            + " order by 1 desc"
            + " limit 1")
    Optional<Integer> findMaxInstancesSeenPerUserByActivityAndVersion(
            @Bind("activityId") long activityId, @Bind("versionId") long versionId);

    /**
     * Helper that only deletes an activity instance and its associated status(es).
     */
    default void deleteByInstanceGuid(String instanceGuid) {
        int numDeleted = getActivityInstanceStatusDao().deleteAllByInstanceGuid(instanceGuid);
        if (numDeleted <= 0) {
            throw new DaoException("No activity instance statuses was deleted");
        }
        numDeleted = getJdbiActivityInstance().deleteByInstanceGuid(instanceGuid);
        if (numDeleted != 1) {
            throw new DaoException("Deleted " + numDeleted + " activity instances");
        }
    }

    default void updateSectionIndexByInstanceGuid(String instanceGuid, int sectionIndex) {
        getJdbiActivityInstance().updateSectionIndex(instanceGuid, sectionIndex);
    }

    @SqlUpdate("delete from activity_instance where activity_instance_id in (<instanceIds>)")
    int _deleteAllInstancesByIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);

    default int deleteAllByIds(Set<Long> instanceIds) {
        getAnswerDao().deleteAllByInstanceIds(instanceIds);
        getActivityInstanceStatusDao().deleteAllByInstanceIds(instanceIds);
        return _deleteAllInstancesByIds(instanceIds);
    }

    default void saveSubstitutions(long instanceId, Map<String, String> substitutions) {
        List<String> variables = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (var key : substitutions.keySet()) {
            variables.add(key);
            values.add(StringUtils.defaultString(substitutions.get(key), ""));
        }
        long[] inserted = getActivityInstanceSql().bulkInsertSubstitutions(instanceId, variables, values);
        DBUtils.checkInsert(variables.size(), inserted.length);
    }

    @SqlQuery("select distinct sub.variable_name"
            + "  from activity_instance_substitution as sub"
            + "  join activity_instance as ai on ai.activity_instance_id = sub.activity_instance_id"
            + "  join (select rev.start_date, rev.end_date"
            + "          from activity_version as ver"
            + "          join revision as rev on rev.revision_id = ver.revision_id"
            + "         where ver.study_activity_id = :activityId and ver.activity_version_id = :versionId"
            + "       ) as ver on ver.start_date <= ai.created_at and (ver.end_date is null or ai.created_at < ver.end_date)"
            + " where ai.study_activity_id = :activityId")
    List<String> findSubstitutionNamesSeenAcrossUsersByActivityAndVersion(
            @Bind("activityId") long activityId, @Bind("versionId") long versionId);

    @SqlQuery("select variable_name, value from activity_instance_substitution where activity_instance_id = :instanceId")
    @KeyColumn("variable_name")
    @ValueColumn("value")
    Map<String, String> findSubstitutions(@Bind("instanceId") long instanceId);

    @SqlQuery("select * from activity_instance_substitution where activity_instance_id in (<instanceIds>)")
    @UseRowReducer(BulkFindSubstitutionsReducer.class)
    Stream<SubstitutionsWrapper> bulkFindSubstitutions(
            @BindList(value = "instanceIds", onEmpty = EmptyHandling.NULL) Set<Long> instanceIds);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryBaseResponsesByInstanceId")
    @RegisterConstructorMapper(FormResponse.class)
    @UseRowReducer(BaseActivityResponsesReducer.class)
    Optional<ActivityResponse> findBaseResponseByInstanceId(@Bind("instanceId") long instanceId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryBaseResponsesByInstanceGuid")
    @RegisterConstructorMapper(FormResponse.class)
    @UseRowReducer(BaseActivityResponsesReducer.class)
    Optional<ActivityResponse> findBaseResponseByInstanceGuid(@Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryBaseResponsesByStudyAndUserGuid")
    @RegisterConstructorMapper(FormResponse.class)
    @UseRowReducer(BaseActivityResponsesReducer.class)
    List<ActivityResponse> findBaseResponsesByStudyAndUserGuid(@Bind("studyGuid") String studyGuid, @Bind("userGuid") String userGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryBaseResponsesByStudyIdAndUserIdsLimitedToActivityIds")
    @RegisterConstructorMapper(FormResponse.class)
    @UseRowReducer(BaseActivityResponsesReducer.class)
    Stream<ActivityResponse> findBaseResponsesByStudyAndUserIds(
            @Bind("studyId") long studyId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @Define("limitActivities") boolean limitActivities,
            @BindList(value = "activityIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> activityIds);

    default Optional<FormResponse> findFormResponseWithAnswersByInstanceId(long instanceId) {
        try (var responseStream = findFormResponsesWithAnswersByInstanceIds(Set.of(instanceId))) {
            return responseStream.findFirst();
        }
    }

    default Optional<FormResponse> findFormResponseWithAnswersByInstanceGuid(String instanceGuid) {
        try (var responseStream = findFormResponsesWithAnswersByInstanceGuids(Set.of(instanceGuid))) {
            return responseStream.findFirst();
        }
    }

    default Stream<FormResponse> findFormResponsesWithAnswersByInstanceIds(Set<Long> instanceIds) {
        return getActivityInstanceSql().findFormResponseWithAnswers(true, instanceIds, null);
    }

    default Stream<FormResponse> findFormResponsesWithAnswersByInstanceGuids(Set<String> instanceGuids) {
        return getActivityInstanceSql().findFormResponseWithAnswers(false, null, instanceGuids);
    }

    default Stream<FormResponse> findFormResponsesWithAnswersByUserIds(long studyId, Set<Long> userIds) {
        return getActivityInstanceSql()
                .findFormResponsesWithAnswersByStudyIdAndUsersWithActivityCodes(
                        studyId, false, true, userIds, null, false, null);
    }

    default Stream<FormResponse> findFormResponsesWithAnswersByUserGuids(long studyId, Set<String> userGuids) {
        return getActivityInstanceSql()
                .findFormResponsesWithAnswersByStudyIdAndUsersWithActivityCodes(
                        studyId, false, false, null, userGuids, false, null);
    }

    default Stream<FormResponse> findFormResponsesSubsetWithAnswersByUserGuids(long studyId,
                                                                               Set<String> userGuids,
                                                                               Set<String> activityCodes) {
        return getActivityInstanceSql()
                .findFormResponsesWithAnswersByStudyIdAndUsersWithActivityCodes(
                        studyId, false, false, null, userGuids, true, activityCodes);
    }

    default Stream<FormResponse> findFormResponsesSubsetWithAnswersByUserGuids(Set<String> userGuids, Set<Long> activityIds) {
        return getActivityInstanceSql()
                .findFormResponsesWithAnswersByUsersAndActivityIds(
                        false, null, userGuids, activityIds);
    }

    default Stream<FormResponse> findFormResponsesSubsetWithAnswersByUserIds(Set<Long> userIds, Set<Long> activityIds) {
        return getActivityInstanceSql()
                .findFormResponsesWithAnswersByUsersAndActivityIds(
                        true, userIds, null, activityIds);
    }

    default Stream<FormResponse> findFormResponsesSubsetWithAnswersByUserId(long userId, Set<Long> activityIds) {
        return findFormResponsesSubsetWithAnswersByUserIds(Set.of(userId), activityIds);
    }

    class BaseActivityResponsesReducer implements LinkedHashMapRowReducer<Long, ActivityResponse> {
        @Override
        public void accumulate(Map<Long, ActivityResponse> container, RowView view) {
            // Only supports form activities now.
            FormResponse instance = view.getRow(FormResponse.class);
            container.put(instance.getId(), instance);
        }
    }

    class BulkFindSubstitutionsReducer implements LinkedHashMapRowReducer<Long, SubstitutionsWrapper> {
        @Override
        public void accumulate(Map<Long, SubstitutionsWrapper> container, RowView view) {
            long instanceId = view.getColumn("activity_instance_id", Long.class);
            String variable = view.getColumn("variable_name", String.class);
            String value = view.getColumn("value", String.class);
            container.computeIfAbsent(instanceId, SubstitutionsWrapper::new).unwrap().put(variable, value);
        }
    }

    // A wrapper around a Map to work better with JDBI for bulk querying substitutions.
    class SubstitutionsWrapper {
        private long activityInstanceId;
        private Map<String, String> subs = new HashMap<>();

        public SubstitutionsWrapper(long activityInstanceId) {
            this.activityInstanceId = activityInstanceId;
        }

        public long getActivityInstanceId() {
            return activityInstanceId;
        }

        public Map<String, String> unwrap() {
            return subs;
        }
    }
}
