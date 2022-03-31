package org.broadinstitute.ddp.studybuilder.translation;

import static java.lang.String.format;
import static org.broadinstitute.ddp.content.VelocityUtil.extractVelocityVariablesFromTemplate;
import static org.broadinstitute.ddp.model.activity.types.TemplateType.TEXT;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingType.PROCESS_ALL_TEMPLATES;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingType.PROCESS_IGNORE_TEMPLATES_WITH_TRANSLATIONS;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectLanguagesToBeAddedToTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectVariablesNotPresentInList;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.getTranslationForLang;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.isTranslationsEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;


/**
 * Contains methods used to add variables and translations for all languages supported in a study.
 * Variables/translations to be added to templates where variables are not defined.
 * NOTE: The variables will be added only if feature 'saveTranslationsToDbJson' is disabled.
 */
@Slf4j
public class TranslationsEnricher {
    /**
     * Max number of variables in a template which defined in activity definition in config files.
     * The thing is that variable value in such template should be moved to translations
     * properties defined in {@link ActivityDef}
     */
    private static final int ACTIVITY_LEVEL_TEMPLATES_VARIABLES_MAX_NUMBER = 1;

    /**
     * Get translations (for all supported languages) by variable defined in a templateText.
     * This method used for assigning activity-level translation-properties from the templates.
     *
     * @param template template which translations to get
     * @return - list of {@link Template} rendered translations
     */
    public static List<Translation> getTranslationByTemplateVariable(Template template, Map<String, TranslationData> allTranslations) {
        if (template != null && template.getVariables() != null && template.getVariables().size() > 0) {
            return allTranslations.keySet().stream()
                    .map(langCde -> createTranslation(template, langCde, allTranslations))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private static Translation createTranslation(Template template, String langCde, Map<String, TranslationData> allTranslations) {
        Collection<String> variablesInTemplate = extractVelocityVariablesFromTemplate(template.getTemplateText());
        if (variablesInTemplate.size() == 0) {
            throw new DDPException("No any variables are defined in activity level templates");
        }
        if (variablesInTemplate.size() > ACTIVITY_LEVEL_TEMPLATES_VARIABLES_MAX_NUMBER) {
            throw new DDPException("Number of variables in activity templates should not exceed "
                    + ACTIVITY_LEVEL_TEMPLATES_VARIABLES_MAX_NUMBER);
        }
        return new Translation(langCde, getTranslationForLang(langCde, variablesInTemplate.iterator().next(), allTranslations));
    }

    /**
     * Add translations to {@link Template} variables (which are detected from {@link Template#getTemplateText()}).<br>
     * Note: The variables will be added only if feature 'saveTranslationsToDbJson' is disabled.
     *
     * <b>Algorithm:</b>
     * <ul>
     *    <li>read all Velocity variables from `templateText';</li>
     *    <li>if sub-element "variables" is not empty then go through it and process each variable:
     *        <ol>
     *           <li>if a variable contains non-empty list of {@link Translation} then:
     *               - add translations to the list (for all languages).
     *           </li>
     *           <li>if a variable contains an empty list of translations then:
     *               - create sub-element "translations";
     *               - 'translationKey' = variable name;
     *               - add translations to the list for all languages.
     *           </li>
     *        </ol>
     *    </li>
     *    <li>detect a list of variables which not exist in sub-element "variables" but exist in 'templateText'
     *        and if this list is not empty then for each of variable do:
     *        <ol>
     *           <li>create sub-element "variable" inside "variables";</li>
     *           <li>create translations list:
     *               - 'translationKey' = variable name;
     *               - add translations to the list for all languages.
     *           </li>
     *        </ol>
     *    </li>
     * </ul>
     */
    public static void addTemplateTranslations(Template template, Map<String, TranslationData> allTranslations) {
        if (isProcessTemplate(template)) {
            if (template.getTemplateType() == null) {
                template.setTemplateType(TEXT);
            }
            // detect list of variables from templateText
            Collection<String> variablesInTemplate = extractVelocityVariablesFromTemplate(template.getTemplateText());
            if (template.getVariables() != null) {
                template.getVariables().forEach(v -> {
                    List<Translation> translations = detectTranslationsForTemplateVariable(
                            v.getTranslations(), v.getName(), allTranslations);
                    v.setTranslation(translations);
                });
            }
            addVariablesToTemplate(template, variablesInTemplate, allTranslations);
        }
    }

    /**
     * Add variables to template.
     * The variables will be added only if feature 'saveTranslationsToDbJson' is disabled.
     */
    private static void addVariablesToTemplate(
            Template template,
            Collection<String> variablesInTemplate,
            Map<String, TranslationData> allTranslations) {

        Collection<String> extraVariables = detectVariablesNotPresentInList(template.getVariables(), variablesInTemplate);
        extraVariables.forEach(v -> {
            template.addVariable(new TemplateVariable(v,
                    detectTranslationsForTemplateVariable(null, v, allTranslations)));
        });
    }

    /**
     * Generates translations list for a specified template variable for all languages defined in a study.
     * Note: it is possible that initial translations list is not empty and it just needs to add it translations for
     * all existing language. But most typical use case: initial translations list is empty and generated
     * references to translations for all defined languages.<br>
     * EXAMPLE:
     * <pre>
     * - defined a template in conf-file (as you can see the variables are not defined):
     * {@code
     *    "bodyTemplate": {"templateType": "HTML", "templateText": """<p class="ddp-question-prompt">$prequal.name *</p>"""}
     * }
     * - in a study defined translations for languages "en", "es" (files "en.conf", "es.conf").
     * - during study building process from this template definition will be built an object {@link Template}
     * with empty list of variables.
     * - this method ({@link #detectTranslationsForTemplateVariable(List, String, Map)}) applied to a template variable
     *   "prequal.name" will generate 2 translation references for each of defined languages:
     * {@code
     *"variables": [{
     *    "name": "prequal.name",
     *    "translations": [
     *        { "language": "en", "text": ${i18n.en.prequal.name} },
     *        { "language": "es", "text": ${i18n.es.prequal.name} },
     *    ]}]
     * }
     * </pre>
     *
     * @param translations     existing array of translations (it can be empty or null)
     * @param templateVariable template variable which equal to a translation key to be detected (for example, "prequal.name")
     * @param allTranslations  all translations for all languages of a study (defined in files in folder 'i18n')
     * @return list of Translations built for all languages of a study.
     */
    private static List<Translation> detectTranslationsForTemplateVariable(
            List<Translation> translations, String templateVariable, Map<String, TranslationData> allTranslations) {
        if (templateVariable != null) {
            List<String> langCdeToAdd = detectLanguagesToBeAddedToTranslations(translations, allTranslations);
            final List<Translation> addedTranslations = !isTranslationsEmpty(translations) ? translations : new ArrayList<>();
            langCdeToAdd.forEach(langCde -> {
                String translationValue = getTranslationForLang(langCde, templateVariable, allTranslations);
                if (translationValue != null) {
                    addedTranslations.add(new Translation(langCde, translationValue));
                    log.debug("Added translation: langCde={}, key={}, value={}", langCde, templateVariable, translationValue);
                } else {
                    throw new RuntimeException(format("Translation not found: langCde=%s, key=%s", langCde, templateVariable));
                }
            });
            return addedTranslations;
        }
        return translations;
    }

    /**
     * Template can be processed if it is not null.
     * Also checked the following conditions:
     * <pre>
     * if processingType == PROCESS_ALL_TEMPLATES then all templates are processed;
     * if processingType = PROCESS_IGNORE_WITH_VARIABLES then process only templates having empty or null variables list.
     * </pre>
     */
    private static boolean isProcessTemplate(Template template) {
        return template != null && StringUtils.isNotBlank(template.getTemplateText())
                && (TranslationsProcessingData.INSTANCE.getTranslationsProcessingType() == PROCESS_ALL_TEMPLATES
                    || (TranslationsProcessingData.INSTANCE.getTranslationsProcessingType() == PROCESS_IGNORE_TEMPLATES_WITH_TRANSLATIONS
                        && (template.getVariables() == null || template.getVariables().size() == 0)));
    }
}
