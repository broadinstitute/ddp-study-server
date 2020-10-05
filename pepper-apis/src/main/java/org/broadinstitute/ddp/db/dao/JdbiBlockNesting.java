package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiBlockNesting extends SqlObject {

    @SqlUpdate("insert into block_nesting (parent_block_id, nested_block_id, display_order, revision_id)"
            + " values (:parentBlockId, :nestedBlockId, :displayOrder, :revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("parentBlockId") long parentBlockId, @Bind("nestedBlockId") long nestedBlockId,
                @Bind("displayOrder") int displayOrder, @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedNestedFormBlockDtosByBlockIdAndInstanceGuid")
    @RegisterRowMapper(FormBlockDto.FormBlockDtoMapper.class)
    List<FormBlockDto> findOrderedNestedFormBlockDtos(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select null as form_section_id, bn.parent_block_id,"
            + "       bt.block_type_code, nested.block_id, nested.block_guid, e.expression_text"
            + "  from block_nesting as bn"
            + "  join block as nested on nested.block_id = bn.nested_block_id"
            + "  join block_type as bt on bt.block_type_id = nested.block_type_id"
            + "  join revision as rev on rev.revision_id = bn.revision_id"
            + "  left join block__expression as be on be.block_id = nested.block_id"
            + "  left join expression as e on e.expression_id = be.expression_id"
            + "  left join revision as be_rev on be_rev.revision_id = be.revision_id"
            + " where bn.parent_block_id in (<blockIds>)"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)"
            + "   and (be.block__expression_id is null or "
            + "       (be_rev.start_date <= :timestamp and (be_rev.end_date is null or :timestamp < be_rev.end_date)))"
            + " order by bn.parent_block_id asc, bn.display_order asc")
    @RegisterRowMapper(FormBlockDto.FormBlockDtoMapper.class)
    List<FormBlockDto> findOrderedNestedDtosByParentIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> parentBlockIds,
            @Bind("timestamp") long timestamp);
}
