package org.broadinstitute.ddp.studybuilder.translation;

import static java.lang.String.format;
import static org.broadinstitute.ddp.content.VelocityUtil.extractVelocityVariablesFromTemplate;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectLanguagesToBeAddedToTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectTranslationKey;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.detectVariablesNotPresentInList;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.getTranslationForLang;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.isTranslationTextContainsKeyName;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.isTranslationsEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains methods used to add translations for all languages supported in a study.
 */
public class TranslationsEnricher {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationsEnricher.class);

    /**
     * Add translations to {@link Template} variables (which are detected from {@link Template#getTemplateText()}).<br>
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
    public static void addTemplateTranslations(Template template, Map<String, Properties> allTranslations) {
        if (template != null) {
            Collection<String> variablesInTemplate = extractVelocityVariablesFromTemplate(template.getTemplateText());
            if (template.getVariables() != null) {
                template.getVariables().forEach(v -> {
                    List<Translation> translations = addTranslations(v.getTranslations(), v.getName(), allTranslations);
                    v.setTranslation(translations);
                });
            }
            Collection<String> extraVariables = detectVariablesNotPresentInList(template.getVariables(), variablesInTemplate);
            extraVariables.forEach(v -> {
                List<Translation> translations = addTranslations(null, v, allTranslations);
                template.addVariable(new TemplateVariable(v, translations));
            });
        }
    }

    /**
     * Add translations to specified array of translations (if it is null then it is created).
     * The result translations list are returned.
     * If parameter `translations` not empty then try to detect translation key from it (from a translation
     * containing in text element a translations key which should start with '$'. Or translation key
     * is taken from parameter `templateVariable`.
     *
     * @param translations      existing array of translations (it can be empty or null)
     * @param templateVariable  template variable which equal to a translation key to be detected
     *                          (in could be null and in that case try to detect a translation key from one of
     *                          translations)
     * @param allTranslations   all translations for all languages of a study
     * @return list of Translations built for all languages of a study.
     */
    public static List<Translation> addTranslations(
            List<Translation> translations, String templateVariable, Map<String, Properties> allTranslations) {
        String translationKey = detectTranslationKey(translations, templateVariable);
        if (translationKey != null) {
            List<String> langCdeToAdd = detectLanguagesToBeAddedToTranslations(translations, allTranslations);
            final List<Translation> updatedTranslations = !isTranslationsEmpty(translations)
                    ? translations.stream().filter(t -> !isTranslationTextContainsKeyName(t)).collect(Collectors.toList())
                    : new ArrayList<>();
            langCdeToAdd.forEach(langCde -> {
                String translationValue = getTranslationForLang(langCde, translationKey, allTranslations);
                if (translationValue != null) {
                    updatedTranslations.add(new Translation(langCde, translationValue));
                    LOG.debug("Added translation: langCde={}, key={}, value={}", langCde, translationKey, translationValue);
                } else {
                    throw new RuntimeException(format("Translation not found: langCde=%s, key=%s", langCde, translationKey));
                }
            });
            return updatedTranslations;
        }
        return translations;
    }

    public static List<Translation> addTranslations(
            List<Translation> translations, Map<String, Properties> allTranslations) {
        return addTranslations(translations, null, allTranslations);
    }
}
