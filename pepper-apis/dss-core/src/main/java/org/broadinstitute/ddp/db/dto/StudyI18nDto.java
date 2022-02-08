package org.broadinstitute.ddp.db.dto;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StudyI18nDto that = (StudyI18nDto) o;
        return languageCode.equals(that.languageCode)
                && name.equals(that.name)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageCode, name, summary);
    }
}
