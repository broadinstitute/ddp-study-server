package org.broadinstitute.ddp.studybuilder.translation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;
import org.broadinstitute.ddp.util.ConfigUtil;

/**
 * Static methods helped to handle translations defined in Study Builder conf files.
 */
public class TranslationsUtil {

    /** Translation key prefix (all references to translations in conf-files starts with this prefix) */
    public static final String I18N = "i18n";

    public static final String LANG_CDE_PLACEHOLDER = "xx";

    /** LangCde length (in a translation key follows after a prefix 'i18n' */
    public static final int LANG_CDE_LENGTH = LANG_CDE_PLACEHOLDER.length();


    /**
     * Read translations from Config (usually 'subs.conf' where translation stored in section 'i18n').
     *
     * @param conf              Config from which to read translations
     * @param translationsPath  section in Config containing translations (usually 'i18n)
     * @return map with translations grouped by langCde
     */
    public static Map<String, TranslationData> getTranslations(Config conf, String translationsPath) {
        Map<String, TranslationData> translationsMap = new HashMap<>();
        conf.getConfig(translationsPath).root().forEach((key, value) -> {
            Config translations = ((ConfigObject) value).toConfig().resolve();
            translationsMap.put(key, new TranslationData(
                    toProperties(translations),
                    ConfigUtil.toJson(translations)));
        });
        return translationsMap;
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
     * Get translation value for certain key and langCode.
     *
     * @param langCde         language code for which to detect a translation
     * @param translationKey  translation key
     * @param allTranslations map with language translations from which to detect a translation value
     * @return a detected translation value, or null - if not detected
     */
    public static String getTranslationForLang(String langCde, String translationKey, Map<String, TranslationData> allTranslations) {
        TranslationData langTranslations = allTranslations.get(langCde);
        if (langTranslations != null) {
            return (String)langTranslations.getProperties().get(translationKey);
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
     * Detect list of langCde which needs to be added to translations array.
     * For example, if a specified array contains translations for 'en', 'es', but in a study
     * specified 'en', 'es', 'fi', 'pl', then it needs to add 'fi' and 'pl'.
     *
     * @param translations     array with translation to be checked
     * @param allTranslations  all translations specified in a study
     * @return list of langCde's which needs to be added to a specified translations array
     */
    public static List<String> detectLanguagesToBeAddedToTranslations(
            List<Translation> translations, Map<String, TranslationData> allTranslations) {
        List<String> langCdeList = new ArrayList<>();
        allTranslations.keySet().forEach(langCde -> {
            boolean addLangCde = true;
            if (!isTranslationsEmpty(translations)) {
                addLangCde = translations.stream().noneMatch(tr -> langCde.equals(tr.getLanguageCode()));
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
                .filter(vtt -> variablesList.stream().noneMatch(v -> v.getName().equals(vtt)))
                .collect(Collectors.toList());
        }
    }
}
