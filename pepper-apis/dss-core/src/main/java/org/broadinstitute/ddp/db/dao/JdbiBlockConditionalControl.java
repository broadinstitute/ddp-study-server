package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBlockConditionalControl extends SqlObject {

    @SqlUpdate("insert into block_conditional_control (block_id, control_question_id) values (:blockId, :controlQuestionId)")
    @GetGeneratedKeys
    long insert(@Bind("blockId") long blockId, @Bind("controlQuestionId") long controlQuestionId);

    @SqlQuery("select bcc.control_question_id"
            + "  from block_conditional_control as bcc"
            + "  join question as q on q.question_id = bcc.control_question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + "  join activity_instance as ai on ai.study_activity_id = q.study_activity_id"
            + " where bcc.block_id = :blockId"
            + "   and ai.activity_instance_guid = :instanceGuid"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    Optional<Long> findControlQuestionId(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select bcc.block_id, bcc.control_question_id"
            + "  from block_conditional_control as bcc"
            + "  join question as q on q.question_id = bcc.control_question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + " where bcc.block_id in (<blockIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @KeyColumn("block_id")
    @ValueColumn("control_question_id")
    Map<Long, Long> findControlQuestionIdsByBlockIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = EmptyHandling.NULL) Iterable<Long> blockIds,
            @Bind("timestamp") long timestamp);
}
