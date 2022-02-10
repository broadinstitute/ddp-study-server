package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.BlockExpressionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBlockExpression extends SqlObject {

    @SqlUpdate("insert into block__expression (block_id, expression_id, revision_id)"
            + " values (:blockId, :expressionId, :revisionId)")
    @GetGeneratedKeys
    long insert(long blockId, long expressionId, long revisionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryExprTextByBlockIdAndRevision")
    Optional<String> getExpressionText(@Bind("blockId") long blockId,
                                       @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select * from block__expression as be"
            + " join revision as rev on rev.revision_id = be.revision_id"
            + " where be.block_id = :blockId and rev.end_date is null")
    @RegisterRowMapper(BlockExpressionDto.BlockExpressionDtoMapper.class)
    Optional<BlockExpressionDto> getActiveByBlockId(long blockId);

    @SqlUpdate("update block__expression set revision_id = :revisionId where block__expression_id = :id")
    int updateRevisionIdById(long id, long revisionId);
}
