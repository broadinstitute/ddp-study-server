package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiLanguageCode extends SqlObject {
    @SqlQuery("select language_code_id from language_code where iso_language_code = ?")
    Long getLanguageCodeId(String languageCode);

    @SqlQuery("select language_code_id, iso_language_code from language_code where iso_language_code = :isoCode")
    @RegisterConstructorMapper(LanguageDto.class)
    LanguageDto findLanguageDtoByCode(@Bind("isoCode") String isoLanguageCode);

    @SqlQuery("select * from language_code")
    @RegisterConstructorMapper(LanguageDto.class)
    List<LanguageDto> findAll();
}
