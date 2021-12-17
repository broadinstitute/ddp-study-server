package org.broadinstitute.ddp.studybuilder.translation;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.broadinstitute.ddp.studybuilder.StudyBuilderCli;

/**
 * Holds the parameters which needs during translations processing.<br>
 * NOTE: this is not a very good approach and in future it's better to refactor class {@link StudyBuilderCli}
 * and pass the context object as a parameter of methods.<br>
 * Basic variables stored in the context:
 * <pre>
 * - translations for all languages (stored in translation files);
 * - translationsProcessingType - storing the type of translations processing;
 * - saveTranslationsToDbJson - boolean flag indicating if translations should be saved to DB table `i18n_translation'
 *   as JSON document.
 * </pre>
 */
public class TranslationsProcessingData {

    public static final TranslationsProcessingData INSTANCE = new TranslationsProcessingData();

    private TranslationsProcessingType translationsProcessingType = null;


    /**
     * Translations data: key = languageCode, value = TranslationData holding Properties and JSON representations
     */
    private Map<String, TranslationData> translations;

    /**
     * key = language code
     * value = language Id (in DB)
     */
    private Map<String, Long> languages = new HashMap<>();

    private boolean saveTranslationsToDbJson;

    public Map<String, TranslationData> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, TranslationData> translations) {
        this.translations = translations;
    }

    public Map<String, Long> getLanguages() {
        return languages;
    }

    public TranslationsProcessingType getTranslationsProcessingType() {
        return translationsProcessingType;
    }

    public void setTranslationsProcessingType(TranslationsProcessingType processTranslations) {
        this.translationsProcessingType = processTranslations;
    }

    public boolean isSaveTranslationsToDbJson() {
        return saveTranslationsToDbJson;
    }

    public void setSaveTranslationsToDbJson(boolean saveTranslationsToDbJson) {
        this.saveTranslationsToDbJson = saveTranslationsToDbJson;
    }


    public static class TranslationData {
        /**
         * Translations as Properties
         */
        Properties properties;

        /**
         * Translations as JSON
         */
        String json;

        public TranslationData(Properties properties, String json) {
            this.properties = properties;
            this.json = json;
        }

        public Properties getProperties() {
            return properties;
        }

        public String getJson() {
            return json;
        }
    }
}
