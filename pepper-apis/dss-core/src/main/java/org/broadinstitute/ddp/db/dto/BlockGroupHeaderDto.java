package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockGroupHeaderDto {
    @ColumnName("block_group_header_id")
    long id;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("list_style_hint_code")
    ListStyleHint listStyleHint;

    @ColumnName("presentation_hint_code")
    PresentationHint presentationHint;

    @ColumnName("title_template_id")
    Long titleTemplateId;

    @ColumnName("revision_id")
    long revisionId;
}
