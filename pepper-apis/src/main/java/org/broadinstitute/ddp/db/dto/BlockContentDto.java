package org.broadinstitute.ddp.db.dto;

import java.beans.ConstructorProperties;

public class BlockContentDto {

    private long id;
    private long blockId;
    private long bodyTemplateId;
    private Long titleTemplateId;
    private long revisionId;

    @ConstructorProperties({"block_content_id", "block_id", "body_template_id", "title_template_id", "revision_id"})
    public BlockContentDto(long id, long blockId, long bodyTemplateId, Long titleTemplateId, long revisionId) {
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
