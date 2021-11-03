package org.broadinstitute.ddp.cache;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.broadinstitute.ddp.model.i18n.I18nTranslation;

/**
 * Cache for storing {@link I18nTranslation}'s.
 */
public class I18nTranslationStore {

    private static final char KEY_SEPARATOR = '_';

    private static final Properties EMPTY_TRANSLATIONS = new Properties();

    /**
     * key = studyId + '_' + langId
     */
    private final Map<String, Properties> studyLangTranslations = new ConcurrentHashMap();
    private final Map<Long, List<String>> templateVariables = new ConcurrentHashMap<>();

    public static final I18nTranslationStore INSTANCE = new I18nTranslationStore();

    public Properties getTranslations(String studyGuid, String isoLangCode) {
        return studyLangTranslations.get(createKey(studyGuid, isoLangCode));
    }

    public Properties putTranslations(String studyGuid, String isoLangCode, Properties translations) {
        studyLangTranslations.put(createKey(studyGuid, isoLangCode), translations);
        return translations;
    }

    public Properties putEmptyTranslations(String studyGuid, String isoLangCode) {
        studyLangTranslations.put(createKey(studyGuid, isoLangCode), EMPTY_TRANSLATIONS);
        return EMPTY_TRANSLATIONS;
    }

    public boolean isEmptyTranslations(Properties properties) {
        return EMPTY_TRANSLATIONS.equals(properties);
    }

    public void putTemplateVariables(Long templateId, List<String> variables) {
        templateVariables.put(templateId, variables);
    }

    public List<String> getTemplateVariables(Long templateId) {
        return templateVariables.get(templateId);
    }

    private static String createKey(String studyGuid, String isoLangCode) {
        return studyGuid + KEY_SEPARATOR + isoLangCode;
    }
}
