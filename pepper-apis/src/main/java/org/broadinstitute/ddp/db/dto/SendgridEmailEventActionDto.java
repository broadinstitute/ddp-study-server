package org.broadinstitute.ddp.db.dto;

public class SendgridEmailEventActionDto {

    private String templateKey;
    private String languageCode;
    private Long linkedActivityId;
    private boolean isDynamic;

    public SendgridEmailEventActionDto(String templateKey, String languageCode) {
        this.templateKey = templateKey;
        this.languageCode = languageCode;
    }

    public SendgridEmailEventActionDto(String templateKey, String languageCode, boolean isDynamic) {
        this.templateKey = templateKey;
        this.languageCode = languageCode;
        this.isDynamic = isDynamic;
    }

    public SendgridEmailEventActionDto(String templateKey, String languageCode, Long linkedActivityId) {
        this.templateKey = templateKey;
        this.languageCode = languageCode;
        this.linkedActivityId = linkedActivityId;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public boolean isDynamicTemplate() {
        return isDynamic;
    }

}

