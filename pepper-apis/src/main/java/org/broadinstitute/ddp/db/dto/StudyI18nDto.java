package org.broadinstitute.ddp.db.dto;

public class StudyI18nDto {
    private String languageCode;
    private String name;
    private String summary;

    public StudyI18nDto(String languageCode, String name, String summary) {
        this.languageCode = languageCode;
        this.name = name;
        this.summary = summary;
    }

    public String getLanguageCode() { 
        return this.languageCode;
    }

    public String getName() {
        return this.name;
    }

    public String getSummary() {
        return this.summary;
    }
}
