package org.broadinstitute.ddp.model.i18n;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public class I18nTranslation {

    private long id;
    private long studyId;
    private long langId;
    private String tanslationDoc;
    private long createdAt;
    private long updatedAt;

    @JdbiConstructor
    public I18nTranslation(@ColumnName("i18n_translation_id") long id,
                           @ColumnName("study_id") long studyId,
                           @ColumnName("language_code_id") long langId,
                           @ColumnName("i18n_translation_doc") String translationDoc,
                           @ColumnName("created_at") long createdAt,
                           @ColumnName("updated_at") long updatedAt) {
        this.id = id;
        this.studyId = studyId;
        this.langId = langId;
        this.tanslationDoc = translationDoc;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getLangId() {
        return langId;
    }

    public String getTanslationDoc() {
        return tanslationDoc;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
