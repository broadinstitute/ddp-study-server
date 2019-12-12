package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TypedQuestionId;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBlockQuestion extends SqlObject {

    @SqlUpdate("insert into block__question(block_id,question_id) values (?,?)")
    @GetGeneratedKeys()
    long insert(long blockId, long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionInfoByBlockIdAndRevision")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> getQuestionDto(@Bind("blockId") long blockId,
                                         @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select qt.question_type_code, q.question_id from block__question as bq"
            + " join question as q on q.question_id = bq.question_id"
            + " join question_type as qt on qt.question_type_id = q.question_type_id"
            + " join revision as rev on rev.revision_id = q.revision_id"
            + " where bq.block_id = :blockId and rev.end_date is null")
    @RegisterRowMapper(TypedQuestionId.TypedQuestionIdMapper.class)
    Optional<TypedQuestionId> getActiveTypedQuestionId(long blockId);

    @SqlQuery("select qt.question_type_code, qsc.stable_id, q.*, rev.start_date as revision_start, rev.end_date as revision_end"
            + "  from block__question as b_q"
            + "  join question as q on b_q.question_id = q.question_id"
            + "  join question_type as qt on q.question_type_id = qt.question_type_id"
            + "  join question_stable_code as qsc on q.question_stable_code_id = qsc.question_stable_code_id"
            + "  join revision as rev on q.revision_id = rev.revision_id"
            + " where b_q.block_id = :blockId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findQuestionDtoByBlockIdAndTimestamp(@Bind("blockId") long blockId,
                                                               @Bind("timestamp") long timestamp);
}
