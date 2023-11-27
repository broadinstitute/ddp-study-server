package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.BlockDto;
import org.broadinstitute.ddp.db.dto.NestedActivityBlockDto;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.stream.Stream;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockTable;

public interface JdbiBlock extends SqlObject {

    default String generateUniqueGuid() {
        return DBUtils.uniqueStandardGuid(getHandle(), BlockTable.TABLE_NAME, BlockTable.GUID);
    }

    @SqlUpdate("insert into block(block_type_id,block_guid) values(?,?)")
    @GetGeneratedKeys()
    long insert(long blockTypeId, String blockGuid);

    @SqlQuery("select block_type_code from block_type where block_type_id = ?")
    String getBlockType(long blockTypeId);

    @SqlQuery("select bt.block_type_code, b.* from block as b, block_type as bt where"
            + " b.block_id = ? and b.block_type_id = bt.block_type_id")
    @RegisterConstructorMapper(BlockDto.class)
    BlockDto findById(long blockId);

    @GetGeneratedKeys
    @SqlUpdate("insert into block_nested_activity (block_id, nested_activity_id, render_hint_id,"
            + "        allow_multiple, add_button_template_id, revision_id)"
            + " select :blockId, :nestedActId, nested_activity_render_hint_id,"
            + "        :allowMultiple, :addButtonTemplateId, :revisionId"
            + "   from nested_activity_render_hint where nested_activity_render_hint_code = :renderHint")
    long insertNestedActivityBlock(
            @Bind("blockId") long blockId,
            @Bind("nestedActId") long nestedActivityId,
            @Bind("renderHint") NestedActivityRenderHint renderHint,
            @Bind("allowMultiple") boolean allowMultiple,
            @Bind("addButtonTemplateId") Long addButtonTemplateId,
            @Bind("revisionId") long revisionId);

    @SqlQuery("select bna.*, rh.nested_activity_render_hint_code as render_hint,"
            + "       (select study_activity_code from study_activity"
            + "         where study_activity_id = bna.nested_activity_id) as nested_activity_code"
            + "  from block_nested_activity as bna"
            + "  join nested_activity_render_hint as rh on rh.nested_activity_render_hint_id = bna.render_hint_id"
            + "  join revision as rev on rev.revision_id = bna.revision_id"
            + "  join activity_instance as ai on ai.activity_instance_guid = :instanceGuid"
            + " where bna.block_id = :blockId"
            + "   and rev.start_date <= ai.created_at"
            + "   and (rev.end_date is null or ai.created_at < rev.end_date)")
    @RegisterConstructorMapper(NestedActivityBlockDto.class)
    Optional<NestedActivityBlockDto> findNestedActivityBlockDto(
            @Bind("blockId") long blockId,
            @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select bna.*, rh.nested_activity_render_hint_code as render_hint,"
            + "       (select study_activity_code from study_activity"
            + "         where study_activity_id = bna.nested_activity_id) as nested_activity_code"
            + "  from block_nested_activity as bna"
            + "  join nested_activity_render_hint as rh on rh.nested_activity_render_hint_id = bna.render_hint_id"
            + "  join revision as rev on rev.revision_id = bna.revision_id"
            + " where bna.block_id in (<blockIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)")
    @RegisterConstructorMapper(NestedActivityBlockDto.class)
    Stream<NestedActivityBlockDto> findNestedActivityBlockDtos(
            @BindList(value = "blockIds", onEmpty = EmptyHandling.NULL) Iterable<Long> blockIds,
            @Bind("timestamp") long timestamp);
}
