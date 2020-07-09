package org.broadinstitute.ddp.model.study;

import java.io.Serializable;
import java.util.Locale;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyLanguage implements Serializable {

    // Explicitly use Expose annotation to tell GSON to serialize
    // Note need to use specially configured GSON serializer to use annotation
    @Expose()
    @SerializedName("languageCode")
    private String languageCode;

    @Expose()
    @SerializedName("displayName")
    private String displayName;

    @Expose()
    @SerializedName("isDefault")
    private boolean isDefault;

    // These fields should not be serialized by GSON but should be serialized
    // by Java serializer
    private long studyId;

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
}
