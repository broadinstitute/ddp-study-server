package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.BlockTabularDto;
import org.broadinstitute.ddp.db.dto.BlockTabularHeaderDto;
import org.broadinstitute.ddp.db.dto.BlockTabularQuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface JdbiBlockTabular extends SqlObject {
    @SqlUpdate("INSERT INTO block_tabular "
            + "         SET block_id      = :blockId, "
            + "             columns_count = :columnsCount, "
            + "             revision_id   = :revisionId")
    @GetGeneratedKeys
    long insert(@Bind("blockId") long blockId,
                @Bind("columnsCount") int columnsCount,
                @Bind("revisionId") long revisionId);

    @SqlUpdate("INSERT INTO block_tabular_header "
            + "         SET block_tabular_id = :tabularBlockId, "
            + "             column_span      = :columnSpan, "
            + "             template_id      = :templateId")
    @GetGeneratedKeys
    long insertHeader(@Bind("tabularBlockId") long tabularBlockId,
                      @Bind("columnSpan") int columnSpan,
                      @Bind("templateId") long templateId);

    @SqlUpdate("INSERT INTO block_tabular_question "
            + "         SET block_tabular_id = :tabularBlockId, "
            + "             question_id      = :questionId, "
            + "             _column          = :column, "
            + "             _row             = :row,"
            + "             column_span   = :columnSpan")
    @GetGeneratedKeys
    long insertQuestion(@Bind("tabularBlockId") long tabularBlockId,
                        @Bind("questionId") long questionId,
                        @Bind("column") int column,
                        @Bind("row") int row,
                        @Bind("columnSpan") int columnSpan);

    @SqlQuery("SELECT bt.*"
            + "  FROM block_tabular as bt"
            + "  JOIN revision as rev ON rev.revision_id = bt.revision_id"
            + " WHERE bt.block_id = :blockId"
            + "   AND rev.start_date <= :timestamp"
            + "   AND (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(BlockTabularDto.class)
    BlockTabularDto findByBlockIdAndTimestamp(@Bind("blockId") long blockId, @Bind("timestamp") long timestamp);

    @SqlQuery("SELECT bth.*"
            + "  FROM block_tabular_header as bth"
            + "  JOIN block_tabular as bt ON bt.block_tabular_id = bth.block_tabular_id"
            + "  JOIN revision as rev ON rev.revision_id = bt.revision_id"
            + " WHERE bt.block_id = :blockId"
            + "   AND rev.start_date <= :timestamp"
            + "   AND (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(BlockTabularHeaderDto.class)
    List<BlockTabularHeaderDto> findHeadersByBlockIdAndTimestamp(@Bind("blockId") long blockId, @Bind("timestamp") long timestamp);

    @SqlQuery("SELECT btq.*, bt.block_id"
            + "  FROM block_tabular_question as btq"
            + "  JOIN block_tabular as bt ON bt.block_tabular_id = btq.block_tabular_id"
            + "  JOIN question as q ON q.question_id = btq.question_id"
            + "  JOIN revision as rev ON rev.revision_id = q.revision_id"
            + " WHERE bt.block_id in (<blockIds>)"
            + "   AND rev.start_date <= :timestamp"
            + "   AND (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(BlockTabularQuestionDto.class)
    List<BlockTabularQuestionDto> findQuestionsByBlockIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = BindList.EmptyHandling.NULL) Iterable<Long> blockIds,
            @Bind("timestamp") long timestamp);
}
