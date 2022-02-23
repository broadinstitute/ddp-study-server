package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockContentDto {
    @ColumnName("block_content_id")
    long id;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("body_template_id")
    long bodyTemplateId;

    @ColumnName("title_template_id")
    Long titleTemplateId;

    @ColumnName("revision_id")
    long revisionId;
}
