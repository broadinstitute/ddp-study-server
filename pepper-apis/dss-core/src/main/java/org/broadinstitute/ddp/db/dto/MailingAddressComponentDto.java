package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import lombok.experimental.Accessors;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.util.Set;

@Value
public class MailingAddressComponentDto extends ComponentDto {
    Long titleTemplateId;
    Long subtitleTemplateId;
    
    @Accessors(fluent = true)
    boolean shouldRequireVerified;
    
    @Accessors(fluent = true)
    boolean shouldRequirePhone;

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
        this.shouldRequireVerified = requireVerified;
        this.shouldRequirePhone = requirePhone;
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
