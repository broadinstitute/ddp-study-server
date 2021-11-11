package org.broadinstitute.ddp.studybuilder;

import java.util.Map;
import java.util.Properties;

import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingType;

/**
 * Holds the StudyBuilder variables which needs to be accessed globally during a study processing.<br>
 * NOTE: this is not a very good approach and in future it's better to refactor class {@link StudyBuilderCli}
 * and pass the context object as a parameter of methods.<br>
 * Basic variables stored in the context:
 * <pre>
 * - translations for all languages (stored in translation files);
 * - translationsProcessingType storing the type of translations processing.
 * </pre>
 */
public class StudyBuilderContext {

    public static final StudyBuilderContext CONTEXT = new StudyBuilderContext();

    private TranslationsProcessingType translationsProcessingType = null;

    private Map<String, Properties> translations;

    public Map<String, Properties> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Properties> translations) {
        this.translations = translations;
    }

    public TranslationsProcessingType getTranslationsProcessingType() {
        return translationsProcessingType;
    }

    public void setTranslationsProcessingType(TranslationsProcessingType processTranslations) {
        this.translationsProcessingType = processTranslations;
    }
}
