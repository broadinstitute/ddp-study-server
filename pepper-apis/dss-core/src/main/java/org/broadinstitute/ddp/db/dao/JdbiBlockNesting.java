package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
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
    @RegisterConstructorMapper(FormBlockDto.class)
    List<FormBlockDto> findOrderedNestedFormBlockDtos(@Bind("blockId") long blockId, @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedNestedDtosByParentIdsAndTimestamp")
    @RegisterConstructorMapper(FormBlockDto.class)
    List<FormBlockDto> findOrderedNestedDtosByParentIdsAndTimestamp(
            @BindList(value = "blockIds", onEmpty = EmptyHandling.NULL_VALUE) Iterable<Long> parentBlockIds,
            @Bind("timestamp") long timestamp);
}
