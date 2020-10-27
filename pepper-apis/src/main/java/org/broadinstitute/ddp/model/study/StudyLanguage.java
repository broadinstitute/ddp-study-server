package org.broadinstitute.ddp.model.study;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.transformers.Exclude;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyLanguage implements Serializable {

    @SerializedName("languageCode")
    private String languageCode;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("isDefault")
    private boolean isDefault;

    @Exclude
    private long studyId;

    @Exclude
    private long languageId;

    @JdbiConstructor
    public StudyLanguage(@ColumnName("iso_language_code") String languageCode,
                         @ColumnName("name") String displayName,
                         @ColumnName("is_default") boolean isDefault,
                         @ColumnName("umbrella_study_id") long studyId,
                         @ColumnName("language_code_id") long languageId) {
        this.languageCode = languageCode;
        this.displayName = displayName;
        this.isDefault = isDefault;
        this.studyId = studyId;
        this.languageId = languageId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getLanguageId() {
        return languageId;
    }

    public Locale toLocale() {
        return Locale.forLanguageTag(languageCode);
    }

    public LanguageDto toLanguageDto() {
        return new LanguageDto(languageId, languageCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StudyLanguage that = (StudyLanguage) o;
        return isDefault == that.isDefault
                && studyId == that.studyId
                && languageId == that.languageId
                && Objects.equals(languageCode, that.languageCode)
                && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageCode, displayName, isDefault, studyId, languageId);
    }
}
