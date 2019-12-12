package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class MailingAddressComponentDto {

    private Long titleTemplateId;
    private Long subtitleTemplateId;

    @JdbiConstructor
    public MailingAddressComponentDto(@ColumnName("title_template_id") Long titleTemplateId,
                                      @ColumnName("subtitle_template_id") Long subtitleTemplateId) {
        this.titleTemplateId = titleTemplateId;
        this.subtitleTemplateId = subtitleTemplateId;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public Long getSubtitleTemplateId() {
        return subtitleTemplateId;
    }
}
