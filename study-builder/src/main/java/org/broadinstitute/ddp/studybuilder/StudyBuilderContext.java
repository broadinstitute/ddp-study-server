package org.broadinstitute.ddp.studybuilder;

import static org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil.TRANSLATION_KEY_PREFIX;

import java.util.Map;
import java.util.Properties;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsUtil;


public class StudyBuilderContext {

    public static final StudyBuilderContext CONTEXT = new StudyBuilderContext();

    public static void readTranslationsFromConfSectionI18n(Config subsCfg) {
        CONTEXT.setTranslations(TranslationsUtil.getTranslations(subsCfg, TRANSLATION_KEY_PREFIX));
    }

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
