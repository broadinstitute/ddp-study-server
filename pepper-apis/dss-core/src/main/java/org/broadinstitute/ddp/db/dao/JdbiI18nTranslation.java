package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Optional;

import org.broadinstitute.ddp.model.i18n.I18nTranslation;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


public interface JdbiI18nTranslation extends SqlObject {

    @SqlQuery("getI18nTranslation")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(I18nTranslation.class)
    Optional<I18nTranslation> getI18nTranslation(@Bind("studyGuid") String studyGuid, @Bind("isoLangCode") String isoLangCode);

    @SqlUpdate("insertI18nTranslation")
    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    long insertI18nTranslation(
            @Bind("translationDoc") String translationDoc,
            @Bind("studyId") long studyId,
            @Bind("languageCodeId") long languageCodeId,
            @Bind("createdAt") long createdAtMillis,
            @Bind("updatedAt") long updatedAtMillis);

    @SqlUpdate("updateI18nTranslation")
    @UseStringTemplateSqlLocator
    long updateI18nTranslation(
            @Bind("translationDoc") String translationDoc,
            @Bind("studyId") long studyId,
            @Bind("languageCodeId") long languageCodeId,
            @Bind("updatedAt") long updatedAtMillis);

    default long insert(String translationDoc, long studyId, long languageCodeId) {
        long now = Instant.now().toEpochMilli();
        return insertI18nTranslation(translationDoc, studyId, languageCodeId, now, now);
    }

    default long update(String translationDoc, long studyId, long languageCodeId) {
        long now = Instant.now().toEpochMilli();
        return updateI18nTranslation(translationDoc, studyId, languageCodeId, now);
    }
}
