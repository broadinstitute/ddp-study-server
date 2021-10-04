package org.broadinstitute.ddp.studybuilder;

import java.util.Map;
import java.util.Properties;

/**
 * Holds the StudyBuilder variables which needs to be accessed globally during a study processing.<br>
 * NOTE: this is not a very good approach and in future it's better to refactor class {@link StudyBuilderCli}
 * and pass the context object as a parameter of methods.<br>
 * Basic variables stored in the context:
 * <pre>
 * - translations for all languages (stored in translation files);
 * - boolean flag `processTranslations` indicating if translations references auto-resolving should happen or not.
 * </pre>
 */
public class StudyBuilderContext {

    public static final StudyBuilderContext CONTEXT = new StudyBuilderContext();

    private boolean processTranslations = false;
    private Map<String, Properties> translations;

    public Map<String, Properties> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Properties> translations) {
        this.translations = translations;
    }

    public boolean isProcessTranslations() {
        return processTranslations;
    }

    public void setProcessTranslations(boolean processTranslations) {
        this.processTranslations = processTranslations;
    }
}
