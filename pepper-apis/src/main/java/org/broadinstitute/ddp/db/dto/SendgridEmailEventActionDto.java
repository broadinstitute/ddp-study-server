package org.broadinstitute.ddp.db.dto;

public class SendgridEmailEventActionDto {

    private String templateKey;
    private String languageCode;
    private Long linkedActivityId;

    public SendgridEmailEventActionDto(String templateKey, String languageCode) {
        this.templateKey = templateKey;
        this.languageCode = languageCode;
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
}

