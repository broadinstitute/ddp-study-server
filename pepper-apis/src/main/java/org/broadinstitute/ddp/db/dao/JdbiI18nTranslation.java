package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.i18n.I18nTranslation;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


public interface JdbiI18nTranslation extends SqlObject {

    @SqlQuery("getI18nTranslations")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(I18nTranslation.class)
    Optional<I18nTranslation> getI18nTranslations(@Bind("studyGuid") String studyGuid, @Bind("isoLangCode") String isoLangCode);
}
