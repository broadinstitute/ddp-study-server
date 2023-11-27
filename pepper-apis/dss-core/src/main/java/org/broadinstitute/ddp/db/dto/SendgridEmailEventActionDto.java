package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Data
public final class SendgridEmailEventActionDto {
    private final List<I18nTemplate> templates = new ArrayList<>();
    private Long linkedActivityId;

    public SendgridEmailEventActionDto(final String templateKey, final String languageCode, final boolean isDynamic) {
        this.templates.add(new I18nTemplate(templateKey, languageCode, isDynamic));
    }

    public SendgridEmailEventActionDto(final String templateKey, final String languageCode,
                                       final Long linkedActivityId, final boolean isDynamic) {
        this(templateKey, languageCode, isDynamic);
        this.linkedActivityId = linkedActivityId;
    }

    public List<I18nTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public void addTemplate(final String templateKey, final String languageCode, final boolean isDynamic) {
        templates.add(new I18nTemplate(templateKey, languageCode, isDynamic));
    }

    @Value
    @AllArgsConstructor
    public static class I18nTemplate {
        String templateKey;
        String languageCode;
        boolean dynamicTemplate;
    }
}

