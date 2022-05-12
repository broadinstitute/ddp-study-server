package org.broadinstitute.ddp.service;

import java.util.Map;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.I18nTranslationStore;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiI18nTranslation;
import org.broadinstitute.ddp.model.i18n.I18nTranslation;
import org.broadinstitute.ddp.util.JsonToMap;
import org.jdbi.v3.core.Handle;

/**
 * Provides reading of translations (stored in table `i18n_translation`).
 * It checks if translations (for certain study/language pair) is stored in translations cache in
 * {@link I18nTranslationStore}.
 * If it’s there then translations are taken from the cache. Otherwise it’s queried from the DB and saved in the
 * {@link I18nTranslationStore}.
 */
@Slf4j
public class I18nTranslationService {
    private static final Gson gson = new Gson();

    public String getTranslation(String name, String studyGuid, String isoLangCode) {
        Properties translations = I18nTranslationStore.INSTANCE.getTranslations(studyGuid, isoLangCode);
        if (I18nTranslationStore.INSTANCE.isEmptyTranslations(translations)) {
            return null;
        } else if (translations == null) {
            translations = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                    handle -> readTranslationsFromDBAndCache(handle, studyGuid, isoLangCode));
            if (I18nTranslationStore.INSTANCE.isEmptyTranslations(translations)) {
                return null;
            }
        }
        String value = translations.getProperty(name);
        if (value == null && !LanguageStore.isDefault(isoLangCode)) {
            log.info("i18n-translations [{}] for StudyGuid={}, isoLangCode={} is not found."
                    + " Try to find for default language {}.", name, studyGuid, isoLangCode, LanguageStore.getDefault());
            return getTranslation(name, studyGuid, LanguageStore.getDefault().getIsoCode());
        }
        return value;
    }

    private Properties readTranslationsFromDBAndCache(Handle handle, String studyGuid, String isoLangCode) {
        log.info("Try to find i18n-translations for StudyGuid={}, isoLangCode={}.", studyGuid, isoLangCode);
        I18nTranslation i18nTranslation = handle.attach(JdbiI18nTranslation.class)
                .getI18nTranslation(studyGuid, isoLangCode)
                .orElse(null);
        Properties translations;
        if (i18nTranslation != null) {
            log.info("i18n-translations for StudyGuid={}, isoLangCode={} are FOUND.", studyGuid, isoLangCode);
            translations = I18nTranslationStore.INSTANCE.putTranslations(
                    studyGuid, isoLangCode, jsonToProperties(i18nTranslation.getTranslationDoc()));
        } else {
            log.warn("i18n-translations for StudyGuid={}, isoLangCode={} are NOT FOUND."
                    + " A default mechanism of translations lookup will be used.", studyGuid, isoLangCode);
            translations = I18nTranslationStore.INSTANCE.putEmptyTranslations(studyGuid, isoLangCode);
        }
        return translations;
    }

    private static Properties jsonToProperties(String jsonDoc) {
        Map<String, String> map = JsonToMap.transformJsonToMapIterative(gson.fromJson(jsonDoc, JsonElement.class));
        Properties properties = new Properties();
        properties.putAll(map);
        return properties;
    }
}
