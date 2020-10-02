package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.BlockGroupHeaderDto;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBlockGroupHeader extends SqlObject {

    @SqlUpdate("insert into block_group_header (block_id, list_style_hint_id, title_template_id, revision_id, presentation_hint_id)"
            + " values (:blockId, :listStyleHintId, :titleTemplateId, :revisionId,"
            + "        (select presentation_hint_id from presentation_hint where presentation_hint_code = :presentation))")
    @GetGeneratedKeys
    long insert(@Bind("blockId") long blockId, @Bind("listStyleHintId") Long listStyleHintId,
                @Bind("titleTemplateId") Long titleTemplateId, @Bind("revisionId") long revisionId,
                @Bind("presentation") PresentationHint presentationHint);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByBlockIdAndInstanceGuid")
    @RegisterRowMapper(BlockGroupHeaderDto.BlockGroupHeaderDtoMapper.class)
    Optional<BlockGroupHeaderDto> findGroupHeaderDto(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByBlockIdAndTimestamp")
    @RegisterRowMapper(BlockGroupHeaderDto.BlockGroupHeaderDtoMapper.class)
    Optional<BlockGroupHeaderDto> findGroupHeaderDto(@Bind("blockId") long blockId, @Bind("timestamp") long timestamp);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDtoByBlockId")
    @RegisterRowMapper(BlockGroupHeaderDto.BlockGroupHeaderDtoMapper.class)
    Optional<BlockGroupHeaderDto> findLatestGroupHeaderDto(@Bind("blockId") long blockId);
}
