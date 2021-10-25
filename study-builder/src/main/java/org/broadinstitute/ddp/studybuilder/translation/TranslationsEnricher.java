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
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.studybuilder.StudyBuilderContext;
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
        if (isProcessTemplate(template)) {
            if (template.getTemplateType() == null) {
                template.setTemplateType(TEXT);
            }
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
     *
     * @param translations     existing array of translations (it can be empty or null)
     * @param templateVariable template variable which equal to a translation key to be detected
     * @param allTranslations  all translations for all languages of a study
     * @return list of Translations built for all languages of a study.
     */
    public static List<Translation> addTranslations(
            List<Translation> translations, String templateVariable, Map<String, Properties> allTranslations) {
        String translationKey = templateVariable;
        if (translationKey != null) {
            List<String> langCdeToAdd = detectLanguagesToBeAddedToTranslations(translations, allTranslations);
            final List<Translation> addedTranslations = !isTranslationsEmpty(translations) ? translations : new ArrayList<>();
            langCdeToAdd.forEach(langCde -> {
                String translationValue = getTranslationForLang(langCde, translationKey, allTranslations);
                if (translationValue != null) {
                    addedTranslations.add(new Translation(langCde, translationValue));
                    LOG.debug("Added translation: langCde={}, key={}, value={}", langCde, translationKey, translationValue);
                } else {
                    throw new RuntimeException(format("Translation not found: langCde=%s, key=%s", langCde, translationKey));
                }
            });
            return addedTranslations;
        }
        return translations;
    }

    /**
     * Get rendered translations of a {@link Template} for all of languages defiend for the current study.
     *
     * @param template template which translations to get
     * @return - list of {@link Template} rendered translations
     */
    public static List<Translation> getTemplateRendered(Template template, Map<String, Properties> allTranslations) {
        if (template != null && template.getVariables() != null && template.getVariables().size() > 0) {
            return allTranslations.keySet().stream()
                    .map(langCde -> new Translation(langCde, template.renderWithDefaultValues(langCde)))
                    .collect(Collectors.toList());
        }
        return null;
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
                && (StudyBuilderContext.CONTEXT.getTranslationsProcessingType() == PROCESS_ALL_TEMPLATES
                    || (StudyBuilderContext.CONTEXT.getTranslationsProcessingType() == PROCESS_IGNORE_TEMPLATES_WITH_TRANSLATIONS
                        && (template.getVariables() == null || template.getVariables().size() == 0)));
    }
}
