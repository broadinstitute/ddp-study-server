package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class I18nTemplateSubstitutionDto {

    private long id;
    private long varId;
    private long langId;
    private long revId;
    private String value;

    @JdbiConstructor
    public I18nTemplateSubstitutionDto(@ColumnName("i18n_template_substitution_id") long id,
                                       @ColumnName("template_variable_id") long varId,
                                       @ColumnName("language_code_id") long langId,
                                       @ColumnName("revision_id") long revId,
                                       @ColumnName("substitution_value") String value) {
        this.id = id;
        this.varId = varId;
        this.langId = langId;
        this.revId = revId;
        this.value = value;
    }

    public long getId() {
        return id;
    }

    public long getVarId() {
        return varId;
    }

    public long getLangId() {
        return langId;
    }

    public long getRevId() {
        return revId;
    }

    public String getValue() {
        return value;
    }
}
