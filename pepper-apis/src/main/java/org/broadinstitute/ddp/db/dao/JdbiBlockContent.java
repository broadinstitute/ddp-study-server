package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBlockContent extends SqlObject {

    @SqlUpdate("insert into block_content (block_id, body_template_id, title_template_id, revision_id)"
            + " values (:blockId, :bodyTemplateId, :titleTemplateId, :revisionId)")
    @GetGeneratedKeys()
    long insert(@Bind("blockId") long blockId, @Bind("bodyTemplateId") long bodyTemplateId,
                @Bind("titleTemplateId") Long titleTemplateId, @Bind("revisionId") long revisionId);


    @SqlUpdate("update block_content set revision_id = :revisionId where block_content_id = :id")
    int updateRevisionById(@Bind("id") long id, @Bind("revisionId") long revisionId);


    @SqlQuery("select bt.* from block_content as bt join revision as r on r.revision_id = bt.revision_id "
            + "where bt.block_id = :blockId and r.end_date is null")
    @RegisterConstructorMapper(BlockContentDto.class)
    Optional<BlockContentDto> findActiveDtoByBlockId(@Bind("blockId") long blockId);

    @SqlQuery("select bt.* from block_content as bt "
            + "join revision as r on r.revision_id = bt.revision_id "
            + "join activity_instance as ai on ai.activity_instance_guid = :instanceGuid "
            + "where bt.block_id = :blockId "
            + "and r.start_date <= ai.created_at and (r.end_date is null or ai.created_at < r.end_date)")
    @RegisterConstructorMapper(BlockContentDto.class)
    Optional<BlockContentDto> findDtoByBlockIdAndInstanceGuid(@Bind("blockId") long blockId,
                                                              @Bind("instanceGuid") String instanceGuid);
}
