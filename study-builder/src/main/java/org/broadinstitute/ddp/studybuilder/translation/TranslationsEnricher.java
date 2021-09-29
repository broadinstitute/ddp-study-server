package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectLanguagesToBeAddedToTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectTranslationKey;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectVariablesNotPresentInList;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.getTranslationForLang;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.isTranslationTextContainsKeyName;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.isTranslationsEmpty;
import static org.broadinstitute.ddp.studybuilder.translation.VelocityUtil.detectVelocityVariablesFromTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;

/**
 * Contains methods used to add translations for all languages supported in a study.
 */
public class TranslationsEnricher {

    /**
     * Add translations to {@link Template} variables.<br>
     *
     * <b>Algorithm:</b>
     * <ul>
     *    <li>read all Velocity variables from `templateText';</li>
     *    <li>if sub-element "variables" is not empty then go through it and process each variable:
     *        <ol>
     *           <li>if a variable contains non-empty list of {@link Translation} then:
     *               - detect from 1st translation a `translationKey';
     *               - add translations to the list (for the rest of languages).
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
    public static void addTemplateTranslations(Template template, Map<String, Properties> allTranslations) {
        if (template != null) {
            List<String> variablesInTemplate = detectVelocityVariablesFromTemplate(template);
            if (template.getVariables() != null) {
                template.getVariables().forEach(v -> {
                    List<Translation> translations = addTranslations(v.getTranslations(), v.getName(), allTranslations);
                    v.setTranslation(translations);
                });
            }
            List<String> extraVariables = detectVariablesNotPresentInList(template.getVariables(), variablesInTemplate);
            extraVariables.forEach(v -> {
                List<Translation> translations = addTranslations(null, v, allTranslations);
                template.getVariables().add(new TemplateVariable(v, translations));
            });
        }
    }

    /**
     * Get translations (for a specified key) for all languages which defined in a study.
     * If parameter `translations` not empty then a translation key detected from it, otherwise
     * it is detected from parameter `templateVariable` (if both are null - then returned null).
     *
     * @param translations      currently existing array of translations (it can be empty)
     * @param templateVariable  template variable which equal to a translation key to be detected
     * @param allTranslations   all translations for all languages of a study
     * @return list of Translations built for all languages of a study.
     */
    public static List<Translation> addTranslations(
            List<Translation> translations, String templateVariable, Map<String, Properties> allTranslations) {
        String translationKey = detectTranslationKey(translations, templateVariable);
        if (translationKey != null) {
            List<String> langCdeToAdd = detectLanguagesToBeAddedToTranslations(translations, allTranslations);
            if (translationKey != null) {
                final List<Translation> updatedTranslations = !isTranslationsEmpty(translations)
                        ? translations.stream().filter(t -> !isTranslationTextContainsKeyName(t)).collect(Collectors.toList())
                        : new ArrayList<>();
                langCdeToAdd.forEach(langCde -> {
                    String translationValue = getTranslationForLang(langCde, translationKey, allTranslations);
                    if (translationValue != null) {
                        updatedTranslations.add(new Translation(langCde, translationValue));
                    }
                });
                return updatedTranslations;
            }
        }
        return translations;
    }

    public static List<Translation> addTranslations(
            List<Translation> translations, Map<String, Properties> allTranslations) {
        return addTranslations(translations, null, allTranslations);
    }
}
