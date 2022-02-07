package org.broadinstitute.ddp.db.dto;

import java.util.Set;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class MailingAddressComponentDto extends ComponentDto {

    private Long titleTemplateId;
    private Long subtitleTemplateId;
    private boolean requireVerified;
    private boolean requirePhone;

    @JdbiConstructor
    public MailingAddressComponentDto(
            @Nested ComponentDto componentDto,
            @ColumnName("title_template_id") Long titleTemplateId,
            @ColumnName("subtitle_template_id") Long subtitleTemplateId,
            @ColumnName("require_verified") boolean requireVerified,
            @ColumnName("require_phone") boolean requirePhone) {
        super(componentDto);
        this.titleTemplateId = titleTemplateId;
        this.subtitleTemplateId = subtitleTemplateId;
        this.requireVerified = requireVerified;
        this.requirePhone = requirePhone;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public Long getSubtitleTemplateId() {
        return subtitleTemplateId;
    }

    public boolean shouldRequireVerified() {
        return requireVerified;
    }

    public boolean shouldRequirePhone() {
        return requirePhone;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        if (titleTemplateId != null) {
            ids.add(titleTemplateId);
        }
        if (subtitleTemplateId != null) {
            ids.add(subtitleTemplateId);
        }
        return ids;
    }
}
