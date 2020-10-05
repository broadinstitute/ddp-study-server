package org.broadinstitute.ddp.db.dao;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dto.BlockGroupHeaderDto;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
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
    @RegisterConstructorMapper(BlockGroupHeaderDto.class)
    Optional<BlockGroupHeaderDto> findGroupHeaderDto(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtosByBlockIdsAndTimestamp")
    @RegisterConstructorMapper(BlockGroupHeaderDto.class)
    Stream<BlockGroupHeaderDto> findDtosByBlockIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> blockIds,
            @Bind("timestamp") long timestamp);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryLatestDtoByBlockId")
    @RegisterConstructorMapper(BlockGroupHeaderDto.class)
    Optional<BlockGroupHeaderDto> findLatestGroupHeaderDto(@Bind("blockId") long blockId);
}
