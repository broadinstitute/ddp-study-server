package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.dto.TypedQuestionId;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBlockQuestion extends SqlObject {

    @SqlUpdate("insert into block__question(block_id,question_id) values (?,?)")
    @GetGeneratedKeys()
    long insert(long blockId, long questionId);

    @SqlQuery("select qt.question_type_code, q.question_id from block__question as bq"
            + " join question as q on q.question_id = bq.question_id"
            + " join question_type as qt on qt.question_type_id = q.question_type_id"
            + " join revision as rev on rev.revision_id = q.revision_id"
            + " where bq.block_id = :blockId and rev.end_date is null")
    @RegisterRowMapper(TypedQuestionId.TypedQuestionIdMapper.class)
    Optional<TypedQuestionId> getActiveTypedQuestionId(long blockId);

    @SqlQuery("select bq.question_id"
            + "  from block__question as bq"
            + "  join question as q on q.question_id = bq.question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + "  join activity_instance as ai on ai.study_activity_id = q.study_activity_id"
            + " where bq.block_id = :blockId"
            + "   and ai.activity_instance_guid = :instanceGuid"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    Optional<Long> findQuestionId(@Bind("blockId") long blockId,
                                  @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select bq.block_id, bq.question_id"
            + "  from block__question as bq"
            + "  join question as q on q.question_id = bq.question_id"
            + "  join revision as rev on rev.revision_id = q.revision_id"
            + " where bq.block_id in (<blockIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @KeyColumn("block_id")
    @ValueColumn("question_id")
    Map<Long, Long> findQuestionIdsByBlockIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> blockIds,
            @Bind("timestamp") long timestamp);
}
