package org.broadinstitute.ddp.model.i18n;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class I18nTranslation {
    @ColumnName("i18n_translation_id")
    long id;

    @ColumnName("study_id")
    long studyId;
    
    @ColumnName("language_code_id")
    long langId;

    @ColumnName("i18n_translation_doc")
    String translationDoc;

    @ColumnName("created_at")
    long createdAt;

    @ColumnName("updated_at")
    long updatedAt;
}
