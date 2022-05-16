package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

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
            + "             _row             = :row")
    @GetGeneratedKeys
    long insertQuestion(@Bind("tabularBlockId") long tabularBlockId,
                        @Bind("questionId") long questionId,
                        @Bind("column") int column,
                        @Bind("row") int row);
}
