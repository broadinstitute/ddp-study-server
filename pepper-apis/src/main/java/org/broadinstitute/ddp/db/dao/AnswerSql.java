package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface AnswerSql extends SqlObject {

    //
    // inserts
    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertAnswerByQuestionStableId")
    long insertAnswerByQuestionStableId(
            @Bind("guid") String answerGuid,
            @Bind("operatorId") long operatorId,
            @Bind("instanceId") long instanceId,
            @Bind("questionStableId") String questionStableId,
            @Bind("createdAt") long createdAtMillis,
            @Bind("updatedAt") long updatedAtMillis);

    //

    @GetGeneratedKeys
    @SqlUpdate("insert into answer (answer_guid, operator_user_id, activity_instance_id, created_at, last_updated_at, question_id)"
            + " values (:guid, :operatorId, :instanceId, :createdAt, :updatedAt, :questionId)")
    long insertAnswer(@Bind("guid") String answerGuid,
                      @Bind("operatorId") long operatorId,
                      @Bind("instanceId") long instanceId,
                      @Bind("questionId") long questionId,
                      @Bind("createdAt") long createdAtMillis,
                      @Bind("updatedAt") long updatedAtMillis);

    @SqlUpdate("insert into agreement_answer (answer_id, answer) values (:answerId, :value)")
    int insertAgreementValue(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("insert into boolean_answer (answer_id, answer) values (:answerId, :value)")
    int insertBoolValue(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("insert into date_answer (answer_id, year, month, day) values (:answerId, :year, :month, :day)")
    int insertDateValue(
            @Bind("answerId") long answerId,
            @Bind("year") Integer year,
            @Bind("month") Integer month,
            @Bind("day") Integer day);

    default int insertDateValue(long answerId, DateValue value) {
        if (value == null) {
            value = new DateValue(null, null, null);
        }
        return insertDateValue(answerId, value.getYear(), value.getMonth(), value.getDay());
    }

    @SqlUpdate("insert into numeric_answer (answer_id, int_value) values (:answerId, :value)")
    int insertNumericIntValue(@Bind("answerId") long answerId, @Bind("value") Long value);

    @GetGeneratedKeys
    @SqlUpdate("insert into picklist_option__answer (answer_id, picklist_option_id, detail_text)"
            + " values (:answerId, :optionId, :detailText)")
    long insertPicklistSelected(
            @Bind("answerId") long answerId,
            @Bind("optionId") long picklistOptionId,
            @Bind("detailText") String detailText);

    @GetGeneratedKeys
    @SqlBatch("insert into picklist_option__answer (answer_id, picklist_option_id, detail_text)"
            + "values (:answerId, :optionId, :detailText)")
    long[] bulkInsertPicklistSelected(
            @Bind("answerId") long answerId,
            @Bind("optionId") List<Long> picklistOptionIds,
            @Bind("detailText") List<String> detailTexts);

    @SqlUpdate("insert into text_answer (answer_id, answer) values (:answerId, :value)")
    int insertTextValue(@Bind("answerId") long answerId, @Bind("value") String value);

    //
    // updates
    //

    @SqlUpdate("update answer set operator_user_id = :operatorId, last_updated_at = :updatedAt where answer_id = :answerId")
    int updateAnswerById(
            @Bind("answerId") long answerId,
            @Bind("operatorId") long operatorId,
            @Bind("updatedAt") long updatedAtMillis);

    @SqlUpdate("update agreement_answer set answer = :value where answer_id = :answerId")
    int updateAgreementValueById(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("update boolean_answer set answer = :value where answer_id = :answerId")
    int updateBoolValueById(@Bind("answerId") long answerId, @Bind("value") boolean value);

    @SqlUpdate("update date_answer set year = :year, month = :month, day = :day where answer_id = :answerId")
    int updateDateValueById(
            @Bind("answerId") long answerId,
            @Bind("year") Integer year,
            @Bind("month") Integer month,
            @Bind("day") Integer day);

    default int updateDateValueById(long answerId, DateValue value) {
        if (value == null) {
            value = new DateValue(null, null, null);
        }
        return updateDateValueById(answerId, value.getYear(), value.getMonth(), value.getDay());
    }

    @SqlUpdate("update numeric_answer set int_value = :value where answer_id = :answerId")
    int updateNumericIntValueById(@Bind("answerId") long answerId, @Bind("value") Long value);

    @SqlUpdate("update text_answer set answer = :value where answer_id = :answerId")
    int updateTextValueById(@Bind("answerId") long answerId, @Bind("value") String value);

    //
    // deletes
    //

    @SqlUpdate("delete from answer where answer_id = :answerId")
    int deleteAnswerById(@Bind("answerId") long answerId);

    @SqlUpdate("delete from answer where answer_id in (<answerIds>)")
    int bulkDeleteAnswersById(@BindList(value = "answerIds", onEmpty = EmptyHandling.NULL) Set<Long> answerIds);

    @SqlUpdate("delete from picklist_option__answer where answer_id = :answerId")
    int deletePicklistSelectedByAnswerId(@Bind("answerId") long answerId);

    //
    // queries
    //

    @SqlQuery("select ai.activity_instance_guid from answer as a"
            + "  join activity_instance as ai on ai.activity_instance_id = a.activity_instance_id"
            + " where a.answer_id = :answerId")
    Optional<String> findInstanceGuidByAnswerId(@Bind("answerId") long answerId);

    @SqlQuery("select a.answer_id, qt.question_type_code from answer as a"
            + "  join question as q on a.question_id = q.question_id"
            + "  join question_type as qt on qt.question_type_id = q.question_type_id"
            + " where a.answer_id in (<answerIds>)")
    @KeyColumn("answer_id")
    @ValueColumn("question_type_code")
    Map<Long, QuestionType> findQuestionTypesByAnswerIds(
            @BindList(value = "answerIds", onEmpty = EmptyHandling.NULL) Set<Long> answerIds);

    @SqlQuery("select a.answer_id from answer as a"
            + "  join activity_instance as ai on ai.activity_instance_id = a.activity_instance_id"
            + " where a.question_id = :questionId and ai.activity_instance_guid = :instanceGuid")
    Set<Long> findAnswerIdsByInstanceGuidAndQuestionId(
            @Bind("instanceGuid") String instanceGuid,
            @Bind("questionId") long questionId);

    @SqlQuery("select answer_id from answer where activity_instance_id in (<instanceIds>)")
    Set<Long> findAllAnswerIdsByInstanceIds(
            @BindList(value = "instanceIds", onEmpty = EmptyHandling.NULL) Set<Long> instanceIds);

    // ddp-automation
    @SqlQuery("select answer_id from answer where operator_user_id = :operatorId")
    Set<Long> findAllAnswerIdsByOperatorUserId(@Bind("operatorId") long operatorUserId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findDtoById")
    @RegisterConstructorMapper(AnswerDto.class)
    Optional<AnswerDto> findDtoById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findDtoByGuid")
    @RegisterConstructorMapper(AnswerDto.class)
    Optional<AnswerDto> findDtoByGuid(@Bind("guid") String guid);

    @UseStringTemplateSqlLocator
    @SqlQuery("findLatestDateValueByQuestionStableIdAndUserId")
    @RegisterConstructorMapper(DateValue.class)
    Optional<DateValue> findLatestDateValueByQuestionStableIdAndUserId(
            @Bind("questionStableId") String questionStableId,
            @Bind("userId") long userId,
            @Bind("studyId") long studyId);
}
