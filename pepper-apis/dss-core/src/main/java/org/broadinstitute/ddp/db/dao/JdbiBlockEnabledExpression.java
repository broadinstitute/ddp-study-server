package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.BlockEnabledExpressionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.Optional;

public interface JdbiBlockEnabledExpression extends SqlObject {

    @SqlUpdate("insert into block_enabled_expression (block_id, expression_id, revision_id)"
            + " values (:blockId, :expressionId, :revisionId)")
    @GetGeneratedKeys
    long insert(long blockId, long expressionId, long revisionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryEnabledExprTextByBlockIdAndRevision")
    Optional<String> getExpressionText(@Bind("blockId") long blockId,
                                       @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select * from block_enabled_expression as be"
            + " join revision as rev on rev.revision_id = be.revision_id"
            + " where be.block_id = :blockId and rev.end_date is null")
    @RegisterConstructorMapper(BlockEnabledExpressionDto.class)
    Optional<BlockEnabledExpressionDto> getActiveByBlockId(long blockId);

    @SqlUpdate("update block_enabled_expression set revision_id = :revisionId where block_enabled_expression_id = :id")
    int updateRevisionIdById(long id, long revisionId);
}
