package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBlockConditionalControl extends SqlObject {

    @SqlUpdate("insert into block_conditional_control (block_id, control_question_id) values (:blockId, :controlQuestionId)")
    @GetGeneratedKeys
    long insert(@Bind("blockId") long blockId, @Bind("controlQuestionId") long controlQuestionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryQuestionDtoByBlockIdAndInstanceGuid")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findControlQuestionDto(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select qt.question_type_code, qsc.stable_id, q.*, rev.start_date as revision_start, rev.end_date as revision_end,"
            + "       tt.tooltip_id as tt_tooltip_id, tt.text_template_id as tt_text_template_id"
            + "  from block_conditional_control as bcc"
            + "  join question as q on q.question_id = bcc.control_question_id"
            + "  join question_type as qt on qt.question_type_id = q.question_type_id"
            + "  join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + "  left join tooltip as tt on tt.tooltip_id = q.tooltip_id"
            + " where bcc.block_id = :blockId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(QuestionDto.class)
    Optional<QuestionDto> findControlQuestionDtoByBlockIdAndTimestamp(@Bind("blockId") long blockId,
                                                                      @Bind("timestamp") long timestamp);
}
