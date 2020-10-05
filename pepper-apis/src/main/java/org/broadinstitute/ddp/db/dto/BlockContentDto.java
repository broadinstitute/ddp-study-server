package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class BlockContentDto {

    private long id;
    private long blockId;
    private long bodyTemplateId;
    private Long titleTemplateId;
    private long revisionId;

    @JdbiConstructor
    public BlockContentDto(
            @ColumnName("block_content_id") long id,
            @ColumnName("block_id") long blockId,
            @ColumnName("body_template_id") long bodyTemplateId,
            @ColumnName("title_template_id") Long titleTemplateId,
            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.blockId = blockId;
        this.bodyTemplateId = bodyTemplateId;
        this.titleTemplateId = titleTemplateId;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getBlockId() {
        return blockId;
    }

    public long getBodyTemplateId() {
        return bodyTemplateId;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }
}
