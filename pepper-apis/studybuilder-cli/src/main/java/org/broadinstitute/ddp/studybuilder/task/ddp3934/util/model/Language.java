package org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model;

import java.util.Locale;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Language {
    private long id;
    private String code;
    
    @JdbiConstructor
    public Language(@ColumnName("language_code_id") long id,
                    @ColumnName("iso_language_code") String code) {
        this.id = id;
        this.code = code;
    }

    public long getId() {
        return id;

    }

    public String getCode() {
        return code;
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(code);
    }
}
