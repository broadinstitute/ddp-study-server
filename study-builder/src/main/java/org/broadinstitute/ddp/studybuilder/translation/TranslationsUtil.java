package org.broadinstitute.ddp.studybuilder.translation;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.broadinstitute.ddp.content.VelocityUtil.VARIABLE_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;

/**
 * Static methods helped to handle translations defined in Study Builder conf files.
 */
public class TranslationsUtil {

    /** Translation key prefix (all references to translations in conf-files starts with this prefix) */
    public static final String TRANSLATION_KEY_PREFIX = "i18n";
    /** Separator in translation key */
    public static final String TRANSLATION_KEY_SEPARATOR = ".";

    public static final String LANG_CDE_PLACEHOLDER = "xx";
    /** LangCde length (in a translation key follows after a prefix 'i18n' */
    public static final int LANG_CDE_LENGTH = LANG_CDE_PLACEHOLDER.length();

    public static final String HOCON_PARAM_PREFIX = "${";
    /** Prefix in Translation HOCON parameter: "${i18n." */
    public static final String TRANSLATION_KEY_HOCON_PREFIX = HOCON_PARAM_PREFIX + TRANSLATION_KEY_PREFIX + TRANSLATION_KEY_SEPARATOR;

    /** Length of substring 'xx.' where xx - language code */
    public static final int LANG_CDE_WITH_SEP_LENGTH = LANG_CDE_LENGTH + TRANSLATION_KEY_SEPARATOR.length();

    /** Length of the translation prefix (in HOCON parameter): "${i18n.xx." */
    public static final int TRANSLATION_KEY_FULL_PREFIX_LENGTH = TRANSLATION_KEY_HOCON_PREFIX.length() + LANG_CDE_WITH_SEP_LENGTH;


    /**
     * Read translations from Config (usually 'subs.conf' where translation stored in section 'i18n').
     *
     * @param conf              Config from which to read translations
     * @param translationsPath  section in Config containing translations (usually 'i18n)
     * @return map with translations grouped by langCde
     */
    public static Map<String, Properties> getTranslations(Config conf, String translationsPath) {
        return groupTranslationsByLangs(toProperties(conf.getConfig(translationsPath).resolve()));
    }

    /**
     * Convert Config to Properties
     */
    public static Properties toProperties(Config config) {
        Properties properties = new Properties();
        config.entrySet().forEach(e -> properties.setProperty(e.getKey(), config.getString(e.getKey())));
        return properties;
    }

    /**
     * Read translations from properties (each translation key starts with prefix 'xx.' where xx - langCde)
     * and group it according to langCde.
     *
     * @param translations  properties with translations (fetched from json file)
     * @return map with translations grouped by langCde
     */
    public static Map<String, Properties> groupTranslationsByLangs(Properties translations) {
        Map<String, Properties> translationsMap = new HashMap<>();
        translations.forEach((k, v) -> {
            String key = ((String)k);
            String langCode = key.substring(0, LANG_CDE_LENGTH);
            translationsMap.putIfAbsent(langCode, new Properties());
            Properties translationsForLang = translationsMap.get(langCode);
            translationsForLang.put(key.substring(LANG_CDE_WITH_SEP_LENGTH), v);
        });
        return translationsMap;
    }

    /**
     * Get translation value for certain key and langCode.
     *
     * @param langCde         language code for which to detect a translation
     * @param translationKey  translation key
     * @param allTranslations map with language translations from which to detect a translation value
     * @return a detected translation value, or null - if not detected
     */
    public static String getTranslationForLang(String langCde, String translationKey, Map<String, Properties> allTranslations) {
        Properties langTranslations = allTranslations.get(langCde);
        if (langTranslations != null) {
            return (String)langTranslations.get(translationKey);
        }
        return null;
    }

    /**
     * Check if translations array is null or empty. It can be empty if template variables are not specified
     * (translation key(s) in such case could be detected from Velocity variables specified in "templateText").
     *
     * @return boolean  true, if translations array is empty
     */
    public static boolean isTranslationsEmpty(List<Translation> translations) {
        return translations == null || translations.size() == 0;
    }

    /**
     * Detect a name of a translation key (if it still not known).
     * For example, if a {@link Translation} is specified like this `{ "language": "en", "text": "$prequal.name" }`
     * then key is "prequal.name" (an eventually a full translation key it will be "i18n.xx.prequal.name" where xx - langCde).
     * NOTE: such approach (to detect a translation key from {@link Translation#getText()} could be used for
     * cases where translations are used without {@link Template}.
     * Such arrays of {@link Translation} could be defined on activity level for some properties like title, subtitle etc.
     * (in the future it is planned to replace these definitions to standard {@link Template}s).
     * If not possible to detect a translation key from {@link Translation}s then use value
     * specified in parameter `translationKey`.
     *
     * @param translations    list of {@link Translation} which is defined in a conf-file: a translation key can be detected
     *                        from any element where 'text' starts with '$' (after resolving):
     *                        i.e. it is not a HOCON placeholder but a text value (example: "$prequal.name");
     *                        the $ means that it is an analogue of a Velocity variable (which name should coincide with
     *                        a translation key)
     * @param translationKey  already known translation key (it could be detected from a {@link Template} (Velocity) variable specified
     *                        in a `templateText'),
     *                        (for example if `"templateText": "<p class=\"PageContent-text\">$prequal.instruction_body</p>"`
     *                        then translationKey = `prequal.instruction_body`)
     * @return String  detected translation key (or null - in case if translations array is null or empty and
     *                 parameter `translationKey` is null either): in such case it is recommended to throw a StudyBuilder
     *                 exception
     */
    public static String detectTranslationKey(List<Translation> translations, String translationKey) {
        String detectedTranslationKey = translationKey;
        if (!isTranslationsEmpty(translations)) {
            detectedTranslationKey = translations.stream()
                    .filter(tr -> isTranslationTextContainsKeyName(tr))
                    .map(t -> detectTranslationShortKey(t))
                    .findFirst().orElse(translationKey);
        }
        return detectedTranslationKey;
    }

    /**
     * Detect list of langCde which needs to be added to translations array.
     * For example, if a specified array contains translations for 'en', 'es', but in a study
     * specified 'en', 'es', 'fi', 'pl', then it needs to add 'fi' and 'pl'.
     *
     * @param translations     array with translation to be checked
     * @param allTranslations  all translations specified in a study
     * @return list of langCde's which needs to be added to a specified translations array
     */
    public static List<String> detectLanguagesToBeAddedToTranslations(
            List<Translation> translations, Map<String, Properties> allTranslations) {
        List<String> langCdeList = new ArrayList<>();
        allTranslations.keySet().forEach(langCde -> {
            boolean addLangCde = true;
            if (!isTranslationsEmpty(translations)) {
                addLangCde = !translations.stream().anyMatch(tr -> langCde.equals(tr.getLanguageCode()))
                        || translations.stream()
                            .anyMatch(tr -> langCde.equals(tr.getLanguageCode()) && isTranslationTextContainsKeyName(tr));
            }
            if (addLangCde) {
                langCdeList.add(langCde);
            }
        });
        return langCdeList;
    }

    /**
     * Get list of variables which exist in `templateText` but not exist in a {@link Template#getVariables()}
     *
     * @param variablesList              template variables list
     * @param variablesFromTemplateText  list of Velocity variables detected in `templateText`
     * @return list of Strings with names of variables
     */
    public static Collection<String> detectVariablesNotPresentInList(
            Collection<TemplateVariable> variablesList, Collection<String> variablesFromTemplateText) {
        if (variablesList == null) {
            return variablesFromTemplateText;
        } else {
            return variablesFromTemplateText.stream()
                .filter(vtt -> variablesFromTemplateText != null && !variablesList.stream().anyMatch(v -> v.getName().equals(vtt)))
                .collect(Collectors.toList());
        }
    }

    /**
     * Check if a {@link Translation} text contains from either
     * a HOCON parameter (like "${i18n.es.prequal.name}") or from name of a
     * Velocity variable (like "$prequal.name")
     */
    public static boolean isTranslationTextContainsKeyName(Translation translation) {
        return translation.getText().length() > 1 && translation.getText().charAt(0) == VARIABLE_PREFIX;
    }

    /**
     * Detect a {@link Translation} short key (without prefix "i18n.xx") from either
     * a HOCON parameter (like "${i18n.es.prequal.name}") or from name of a
     * Velocity variable (like "$prequal.name").
     */
    public static String detectTranslationShortKey(Translation translation) {
        int subsIndexStart = 1;
        int subsIndexEnd = translation.getText().length();
        if (startsWith(translation.getText(), TRANSLATION_KEY_HOCON_PREFIX)) {
            subsIndexStart = TRANSLATION_KEY_FULL_PREFIX_LENGTH;
            --subsIndexEnd;
        }
        return translation.getText().substring(subsIndexStart, subsIndexEnd);
    }
}
