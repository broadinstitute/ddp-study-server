package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class SectionBlockMembershipDto {
    @ColumnName("form_section__block_id")
    private final long id;

    @ColumnName("form_section_id")
    private final long sectionId;

    @ColumnName("block_id")
    private final long blockId;

    @ColumnName("display_order")
    private int displayOrder;

    @ColumnName("revision_id")
    private final long revisionId;
}
