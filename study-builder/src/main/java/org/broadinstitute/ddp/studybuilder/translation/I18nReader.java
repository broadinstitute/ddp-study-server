package org.broadinstitute.ddp.studybuilder.translation;

import static org.apache.commons.collections.MapUtils.isNotEmpty;
import static org.broadinstitute.ddp.studybuilder.BuilderUtils.getResourceFolderFiles;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.LANG_CDE_LENGTH;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.TRANSLATION_KEY_PREFIX;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;

/**
 * Provides reading of Translations from i18n files (having name format: `xx.conf` where xx - langCde).
 * If path with i18n files is specified then read translation files from this folder
 * (this parameter has higher priority than `subsConf`).
 * If `i1bnPath` is null or no any i18n files are found in it then try to read i18n translations from `subsCfg`.
 */
public class I18nReader {

    public static final String CONF_EXT = "conf";
    public static final char FILE_EXT_SEP = '.';

    public static Map<String, Properties> readI18nTranslations(Config subsCfg, String i18nPath) {
        Map<String, Properties> translations = readTranslationsFromFilesInSpecifiedFolder(i18nPath);
        return isNotEmpty(translations) ? translations : TranslationsUtil.getTranslations(subsCfg, TRANSLATION_KEY_PREFIX);
    }

    /**
     * Reads translations directly from files in a specified folder.
     *
     * <p><b>Algorithm:</b>
     * <pre>
     *     - read files from a specified folder;
     *     - if a file conforms to format 'xx.conf' (where 'xx' - langCde) then:
     *     -- parse the file;
     *     -- put to Map of properties with a key = langCde.
     * </pre>
     */
    public static Map<String, Properties> readTranslationsFromFilesInSpecifiedFolder(String i18nPath) {
        if (StringUtils.isNotBlank(i18nPath)) {
            File[] files = getResourceFolderFiles(i18nPath);
            Map<String, Properties> translationsMap = new HashMap<>();
            if (files != null) {
                for (File f : files) {
                    String langCde = detectLangCde(f.getName());
                    if (langCde != null) {
                        addTranslationsToMap(f, langCde, translationsMap);
                    }
                }
                if (translationsMap.size() > 0) {
                    return translationsMap;
                }
            }
        }
        return null;
    }

    private static void addTranslationsToMap(File i18nFile, String langCde, Map<String, Properties> translationsMap) {
        translationsMap.putIfAbsent(langCde, new Properties());
        Properties translations = translationsMap.get(langCde);
        translations.putAll(TranslationsUtil.toProperties(ConfigFactory.parseFile(i18nFile)));
    }

    private static String detectLangCde(String fileName) {
        String[] fileParts = StringUtils.split(fileName, FILE_EXT_SEP);
        if (isI18nFile(fileParts)) {
            return fileParts[0];
        }
        return null;
    }

    private static boolean isI18nFile(String[] fileParts) {
        return fileParts.length == 2 && CONF_EXT.equalsIgnoreCase(fileParts[1]) && fileParts[0].length() == LANG_CDE_LENGTH;
    }
}
