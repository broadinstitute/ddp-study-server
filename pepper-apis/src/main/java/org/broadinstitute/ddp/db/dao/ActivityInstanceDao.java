package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
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


    /**
     * Convenience method to create new activity instance when both operator and participant is the same, and using defaults.
     */
    default ActivityInstanceDto insertInstance(long activityId, String userGuid) {
        long millis = Instant.now().toEpochMilli();
        return insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false, millis);
    }

    /**
     * Convenience method to create new activity instance at the current time.
     */
    default ActivityInstanceDto insertInstance(long activityId,
                                               String operatorGuid,
                                               String participantGuid,
                                               InstanceStatusType initialStatus,
                                               boolean isReadOnly) {
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
                                               InstanceStatusType initialStatus, boolean isReadOnly,
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
                                               InstanceStatusType initialStatus, boolean isReadOnly,
                                               long createdAtMillis, Long onDemandTriggerId) {
        JdbiActivityInstance jdbiInstance = getJdbiActivityInstance();
        ActivityInstanceStatusDao statusDao = getActivityInstanceStatusDao();

        String instanceGuid = jdbiInstance.generateUniqueGuid();
        long participantId = getJdbiUser().getUserIdByGuid(participantGuid);
        long instanceId = jdbiInstance.insert(activityId, participantId, instanceGuid, isReadOnly,
                createdAtMillis, onDemandTriggerId);
        statusDao.insertStatus(instanceId, initialStatus, createdAtMillis, operatorGuid);

        return jdbiInstance.getByActivityInstanceId(instanceId).orElse(null);
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
                                               InstanceStatusType initialStatus, boolean isReadOnly,
                                               long createdAtMillis, Long submissionId,
                                               String sessionId, String legacyVersion) {
        JdbiActivityInstance jdbiInstance = getJdbiActivityInstance();
        ActivityInstanceStatusDao statusDao = getActivityInstanceStatusDao();

        String instanceGuid = jdbiInstance.generateUniqueGuid();
        long participantId = getJdbiUser().getUserIdByGuid(participantGuid);
        long instanceId = jdbiInstance.insertLegacyInstance(activityId, participantId, instanceGuid,
                isReadOnly, createdAtMillis, submissionId, sessionId, legacyVersion);
        //statusDao.insertStatus(instanceId, initialStatus, createdAtMillis, operatorGuid);

        return jdbiInstance.getByActivityInstanceId(instanceId).orElse(null);
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

    @SqlUpdate("delete from activity_instance where activity_instance_id in (<instanceIds>)")
    int _deleteAllInstancesByIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);

    default int deleteAllByIds(Set<Long> instanceIds) {
        getAnswerDao().deleteAllByInstanceIds(instanceIds);
        getActivityInstanceStatusDao().deleteAllByInstanceIds(instanceIds);
        return _deleteAllInstancesByIds(instanceIds);
    }


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

    default Stream<FormResponse> findFormResponsesWithAnswersByUserIds(long studyId, Set<Long> userIds) {
        return _findFormResponsesWithAnswersByStudyIdAndUsersWithActivitiesLimit(studyId, false, true, userIds, null, false, null);
    }

    default Stream<FormResponse> findFormResponsesWithAnswersByUserGuids(long studyId, Set<String> userGuids) {
        return _findFormResponsesWithAnswersByStudyIdAndUsersWithActivitiesLimit(studyId, false, false, null, userGuids, false, null);
    }

    default Stream<FormResponse> findFormResponsesSubsetWithAnswersByUserGuids(long studyId,
                                                                               Set<String> userGuids,
                                                                               Set<String> activityCodes) {
        return _findFormResponsesWithAnswersByStudyIdAndUsersWithActivitiesLimit(
                studyId, false, false, null, userGuids, true, activityCodes);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryFormResponsesSubsetWithAnswersByStudyId")
    @RegisterConstructorMapper(value = FormResponse.class, prefix = "a")
    @UseRowReducer(FormResponsesWithAnswersForUsersReducer.class)
    Stream<FormResponse> _findFormResponsesWithAnswersByStudyIdAndUsersWithActivitiesLimit(
            @Bind("studyId") long studyId,
            @Define("selectAll") boolean selectAll,
            @Define("byId") boolean byId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids,
            @Define("limitActivities") boolean limitActivities,
            @BindList(value = "activityCodes", onEmpty = BindList.EmptyHandling.NULL) Set<String> activityCodes);


    class BaseActivityResponsesReducer implements LinkedHashMapRowReducer<Long, ActivityResponse> {
        @Override
        public void accumulate(Map<Long, ActivityResponse> container, RowView view) {
            // Only supports form activities now.
            FormResponse instance = view.getRow(FormResponse.class);
            container.put(instance.getId(), instance);
        }
    }

    class FormResponsesWithAnswersForUsersReducer implements LinkedHashMapRowReducer<Long, FormResponse> {
        private Map<Long, Answer> childAnswers = new HashMap<>();

        @Override
        public void accumulate(Map<Long, FormResponse> container, RowView view) {
            long instanceId = view.getColumn("a_instance_id", Long.class);
            FormResponse response = container.computeIfAbsent(instanceId, id -> view.getRow(FormResponse.class));

            Long answerId = view.getColumn("answer_id", Long.class);
            if (answerId == null) {
                return;
            }

            String answerGuid = view.getColumn("answer_guid", String.class);
            String questionStableId = view.getColumn("question_stable_id", String.class);
            QuestionType type = QuestionType.valueOf(view.getColumn("question_type", String.class));
            boolean isChildAnswer = view.getColumn("is_child_answer", Boolean.class);

            Answer answer;
            switch (type) {
                case AGREEMENT:
                    answer = new AgreementAnswer(answerId, questionStableId, answerGuid, view.getColumn("aa_value", Boolean.class));
                    break;
                case BOOLEAN:
                    answer = new BoolAnswer(answerId, questionStableId, answerGuid, view.getColumn("ba_value", Boolean.class));
                    break;
                case TEXT:
                    answer = new TextAnswer(answerId, questionStableId, answerGuid, view.getColumn("ta_value", String.class));
                    break;
                case DATE:
                    answer = new DateAnswer(answerId, questionStableId, answerGuid,
                            view.getColumn("da_year", Integer.class),
                            view.getColumn("da_month", Integer.class),
                            view.getColumn("da_day", Integer.class));
                    break;
                case NUMERIC:
                    NumericType numericType = NumericType.valueOf(view.getColumn("na_numeric_type", String.class));
                    if (numericType == NumericType.INTEGER) {
                        answer = new NumericIntegerAnswer(answerId, questionStableId, answerGuid,
                                view.getColumn("na_int_value", Long.class));
                    } else {
                        throw new DaoException("Unhandled numeric answer type " + numericType);
                    }
                    break;
                case PICKLIST:
                    if (isChildAnswer) {
                        answer = childAnswers.computeIfAbsent(answerId, id ->
                                new PicklistAnswer(answerId, questionStableId, answerGuid, new ArrayList<>()));
                    } else {
                        answer = response.getAnswerOrCompute(questionStableId, () ->
                                new PicklistAnswer(answerId, questionStableId, answerGuid, new ArrayList<>()));
                    }
                    String optionStableId = view.getColumn("pa_option_stable_id", String.class);
                    if (optionStableId != null) {
                        SelectedPicklistOption option = new SelectedPicklistOption(
                                optionStableId,
                                view.getColumn("pa_detail_text", String.class));
                        ((PicklistAnswer) answer).getValue().add(option);
                    }
                    break;
                case COMPOSITE:
                    answer = response.getAnswerOrCompute(questionStableId, () -> {
                        CompositeAnswer ans = new CompositeAnswer(answerId, questionStableId, answerGuid);
                        ans.setAllowMultiple(view.getColumn("ca_allow_multiple", Boolean.class));
                        ans.setUnwrapOnExport(view.getColumn("ca_unwrap_on_export", Boolean.class));
                        return ans;
                    });
                    Long childAnswerId = view.getColumn("ca_child_answer_id", Long.class);
                    if (childAnswerId != null) {
                        Answer childAnswer = childAnswers.get(childAnswerId);
                        if (childAnswer == null) {
                            throw new DaoException(String.format(
                                    "could not find child answer with id=%d for composite answer %d", childAnswerId, answerId));
                        }
                        int childRow = view.getColumn("ca_child_row", Integer.class); // zero-indexed row number
                        int childCol = view.getColumn("ca_child_col", Integer.class); // zero-indexed column number
                        List<AnswerRow> rows = ((CompositeAnswer) answer).getValue();
                        while (rows.size() < childRow + 1) {
                            rows.add(new AnswerRow());
                        }
                        List<Answer> row = rows.get(childRow).getValues();
                        while (row.size() < childCol + 1) {
                            row.add(null);
                        }
                        row.set(childCol, childAnswer);
                    }
                    return;     // Parent composite answer is already in response, and composites are currently not supported as children.
                default:
                    throw new DaoException("Unhandled answer type " + type);
            }

            if (isChildAnswer) {
                childAnswers.put(answerId, answer);
            } else {
                response.putAnswer(answer);
            }
        }
    }
}
