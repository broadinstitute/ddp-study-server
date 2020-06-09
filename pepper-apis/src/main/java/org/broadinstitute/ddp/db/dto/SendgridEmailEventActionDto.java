package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

public class SendgridEmailEventActionDto {

    private List<I18nTemplate> templates;
    private Long linkedActivityId;

    public SendgridEmailEventActionDto(String templateKey, String languageCode) {
        this.templates = new ArrayList<>();
        this.templates.add(new I18nTemplate(templateKey, languageCode));
    }

    public SendgridEmailEventActionDto(String templateKey, String languageCode, Long linkedActivityId) {
        this.linkedActivityId = linkedActivityId;
        this.templates = new ArrayList<>();
        this.templates.add(new I18nTemplate(templateKey, languageCode));
    }

    public List<I18nTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public void addTemplate(String templateKey, String languageCode) {
        templates.add(new I18nTemplate(templateKey, languageCode));
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public void setLinkedActivityId(Long linkedActivityId) {
        this.linkedActivityId = linkedActivityId;
    }

    public static class I18nTemplate {
        private String templateKey;
        private String languageCode;

        public I18nTemplate(String templateKey, String languageCode) {
            this.templateKey = templateKey;
            this.languageCode = languageCode;
        }

        public String getTemplateKey() {
            return templateKey;
        }

        public String getLanguageCode() {
            return languageCode;
        }
    }
}

