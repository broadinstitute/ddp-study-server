package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

public interface JdbiQuestionValidation extends SqlObject {

    @SqlUpdate("insert into question__validation(question_id,validation_id) values(:questionId,:validationId)")
    @GetGeneratedKeys
    long insert(long questionId, long validationId);

    @SqlQuery("select vt.validation_type_code, val.* from question__validation as qv"
            + " join validation as val on val.validation_id = qv.validation_id"
            + " join validation_type as vt on vt.validation_type_id = val.validation_type_id"
            + " join revision as rev on rev.revision_id = val.revision_id"
            + " where qv.question_id = :questionId and rev.end_date is null")
    @RegisterRowMapper(ValidationDto.ValidationDtoMapper.class)
    List<ValidationDto> getAllActiveValidations(long questionId);

    default List<ValidationDto> getAllActiveValidations(QuestionDto questionDto) {
        return getAllActiveValidations(questionDto.getId());
    }

    @SqlQuery("select qv.question_id, vt.validation_type_code, val.* from question__validation as qv"
            + " join validation as val on val.validation_id = qv.validation_id"
            + " join validation_type as vt on vt.validation_type_id = val.validation_type_id"
            + " join revision as rev on rev.revision_id = val.revision_id"
            + " join question as q on qv.question_id = q.question_id"
            + " where q.study_activity_id = :activityId and rev.end_date is null")
    @RegisterRowMapper(ValidationDto.ValidationDtoMapper.class)
    @UseRowReducer(ValidationRowReducer.class)
    Map<Long, List<ValidationDto>> getAllActiveValidationsForActivity(@Bind("activityId") long activityId);

    @SqlQuery("select vt.validation_type_code, val.* from question__validation as qv"
            + " join validation as val on val.validation_id = qv.validation_id"
            + " join validation_type as vt on vt.validation_type_id = val.validation_type_id"
            + " join revision as rev on rev.revision_id = val.revision_id"
            + " where qv.question_id = :questionId and vt.validation_type_code = 'REQUIRED' and rev.end_date is null")
    @RegisterRowMapper(ValidationDto.ValidationDtoMapper.class)
    Optional<ValidationDto> getRequiredValidationIfActive(long questionId);

    @SqlQuery("select vt.validation_type_code, val.*"
            + "  from question__validation as qv"
            + "  join validation as val on val.validation_id = qv.validation_id"
            + "  join validation_type as vt on vt.validation_type_id = val.validation_type_id"
            + "  join revision as rev on rev.revision_id = val.revision_id"
            + " where qv.question_id = :questionId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterRowMapper(ValidationDto.ValidationDtoMapper.class)
    List<ValidationDto> findDtosByQuestionIdAndTimestamp(@Bind("questionId") long questionId,
                                                         @Bind("timestamp") long timestamp);


    class ValidationRowReducer implements LinkedHashMapRowReducer<Long, Map.Entry<Long, List<ValidationDto>>> {

        @Override
        public void accumulate(Map<Long, Map.Entry<Long, List<ValidationDto>>> map, RowView rowView) {
            Long questionId = rowView.getColumn("question_id", Long.class);
            Map.Entry<Long, List<ValidationDto>> entry = map.computeIfAbsent(questionId,
                    id -> Map.entry(id, new ArrayList()));
            entry.getValue(). add(rowView.getRow(ValidationDto.class));

        }
    }

}
