package org.broadinstitute.ddp.cache;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.i18n.I18nTranslation;

/**
 * Cache for storing {@link I18nTranslation}'s.
 * It stores translations fetched from DB table 'i18n_translation`.
 * The storage is a Map where key = studyGuid + isoLangCode and value = Properties containing
 * translations which stored in JSON format in column `i18n_translation_doc'.
 */
public class I18nTranslationStore {

    private static final char KEY_SEPARATOR = '_';

    private static final Properties EMPTY_TRANSLATIONS = new Properties();

    /**
     * Translations cache.<br>
     * key = studyGuid + '_' + isoLangCode;
     * value = Properties with translations (converted from JSON)
     */
    private final Map<String, Properties> studyLangTranslations = new ConcurrentHashMap();

    /**
     * Cache with template variables (extracted from {@link Template#getTemplateText()}.
     * This cache stores variables in order to avoid `templateText` repeated parsing..
     * This parsing (to fetch variables from a template text) is done because in future
     * the variables and translations won't be stored in DB tables 'template_variable' and
     * 'i18n_template_substitution', because all translations will be stored in DB table `i18n_translation'
     * and will be detected directly by variable names extracted from {@link Template#getTemplateText()}.
     */
    private final Map<Long, List<String>> templateVariables = new ConcurrentHashMap<>();

    /**
     * Instance of the class {@link I18nTranslationStore}
     */
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
