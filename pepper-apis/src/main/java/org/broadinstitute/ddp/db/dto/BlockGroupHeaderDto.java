package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class BlockGroupHeaderDto {

    private long id;
    private long blockId;
    private ListStyleHint listStyleHint;
    private PresentationHint presentationHint;
    private Long titleTemplateId;
    private long revisionId;

    @JdbiConstructor
    public BlockGroupHeaderDto(
            @ColumnName("block_group_header_id") long id,
            @ColumnName("block_id") long blockId,
            @ColumnName("list_style_hint_code") ListStyleHint listStyleHint,
            @ColumnName("presentation_hint_code") PresentationHint presentationHint,
            @ColumnName("title_template_id") Long titleTemplateId,
            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.blockId = blockId;
        this.listStyleHint = listStyleHint;
        this.presentationHint = presentationHint;
        this.titleTemplateId = titleTemplateId;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getBlockId() {
        return blockId;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public PresentationHint getPresentationHint() {
        return presentationHint;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }
}
