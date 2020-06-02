package org.broadinstitute.ddp.model.study;

import com.google.gson.annotations.SerializedName;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyLanguage {
    @SerializedName("languageCode")
    private String languageCode;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("isDefault")
    private boolean isDefault;
    private transient long studyId;
    private transient long languageId;

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

    public boolean getIsDefault() {
        return isDefault;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getLanguageId() {
        return languageId;
    }
}
