package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

public class SendgridEmailEventActionDto {

    private List<I18nTemplate> templates;
    private Long linkedActivityId;

    public SendgridEmailEventActionDto(String templateKey, String languageCode, boolean isDynamic) {
        this.templates = new ArrayList<>();
        this.templates.add(new I18nTemplate(templateKey, languageCode, isDynamic));
    }

    public SendgridEmailEventActionDto(String templateKey, String languageCode, Long linkedActivityId, boolean isDynamic) {
        this.linkedActivityId = linkedActivityId;
        this.templates = new ArrayList<>();
        this.templates.add(new I18nTemplate(templateKey, languageCode, isDynamic));
    }

    public List<I18nTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public void addTemplate(String templateKey, String languageCode, boolean isDynamic) {
        templates.add(new I18nTemplate(templateKey, languageCode, isDynamic));
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
        private boolean isDynamicTemplate;

        public I18nTemplate(String templateKey, String languageCode, boolean isDynamicTemplate) {
            this.templateKey = templateKey;
            this.languageCode = languageCode;
            this.isDynamicTemplate = isDynamicTemplate;
        }

        public String getTemplateKey() {
            return templateKey;
        }

        public String getLanguageCode() {
            return languageCode;
        }

        public boolean isDynamicTemplate() {
            return isDynamicTemplate;
        }
    }
}

