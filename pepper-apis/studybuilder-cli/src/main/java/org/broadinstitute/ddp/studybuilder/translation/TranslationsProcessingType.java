package org.broadinstitute.ddp.studybuilder.translation;

/**
 * Defines different types of translations processing (translation references generation).
 */
public enum TranslationsProcessingType {

    /**
     * Process (generate translations) for all templates. Even that ones where variables with translations
     * are defined and it needs to generate translations for some new language which was introduced in a study.
     * NOTE: translations having an empty 'translationText' are skipped.
     */
    PROCESS_ALL_TEMPLATES,

    /**
     * Process (generate translations) only for templates which has no variables (or where number of variables
     * is 0, i.e. empty variables list).
     * This approach is good when we want to remove variables definitions in a study but don't want to do it
     * for all config files. Or may be we need to add new activities to existing studies and do not want
     * to define variables/translations in templates of new activities.
     * NOTE: this approach will start to work when Pepper will support study data versioning (when we
     * could generate a new version of study data from updates study config files).
     * NOTE: it is also ignored translations having an empty 'translationText'.
     */
    PROCESS_IGNORE_TEMPLATES_WITH_TRANSLATIONS
}
