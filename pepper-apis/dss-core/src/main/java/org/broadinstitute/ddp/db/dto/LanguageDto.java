package org.broadinstitute.ddp.db.dto;

import java.util.Locale;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class LanguageDto {
    private long id;
    // 2-letter ISO language code
    private String isoCode;

    @JdbiConstructor
    public LanguageDto(
            @ColumnName("language_code_id") long id,
            @ColumnName("iso_language_code") String isoCode
    ) {
        this.id = id;
        this.isoCode = isoCode;
    }

    public long getId() {
        return id;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public Locale toLocale() {
        return Locale.forLanguageTag(isoCode);
    }
}
