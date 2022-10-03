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

    public SendgridEmailEventActionDto(final String templateKey, final String languageCode) {
        this.templates.add(new I18nTemplate(templateKey, languageCode));
    }

    public SendgridEmailEventActionDto(final String templateKey, final String languageCode,
                                       final Long linkedActivityId) {
        this(templateKey, languageCode);
        this.linkedActivityId = linkedActivityId;
    }

    public List<I18nTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public void addTemplate(final String templateKey, final String languageCode) {
        templates.add(new I18nTemplate(templateKey, languageCode));
    }

    @Value
    @AllArgsConstructor
    public static class I18nTemplate {
        String templateKey;
        String languageCode;
    }
}

