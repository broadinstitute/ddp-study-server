package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

import java.util.List;

public class TranslationUtil {

    public static Translation extractTranslatedActivityName(FormActivityDef def, String preferredLangCode, String studyDefaultLangCode) {
        Translation preferredName = null;
        Translation studyDefaultName = null;
        for (var name : def.getTranslatedNames()) {
            if (name.getLanguageCode().equals(studyDefaultLangCode)) {
                studyDefaultName = name;
            }
            if (name.getLanguageCode().equals(preferredLangCode)) {
                preferredName = name;
            }
        }
        if (preferredName == null && studyDefaultName == null) {
            throw new DDPException("Could not find name for activity " + def.getActivityCode());
        }
        return preferredName != null ? preferredName : studyDefaultName;
    }

    public static String extractOptionalActivityTranslation(List<Translation> translations, String isoLangCode) {
        return translations.stream()
                .filter(trans -> trans.getLanguageCode().equals(isoLangCode))
                .map(Translation::getText)
                .findFirst()
                .orElse(null);
    }

    public static String extractOptionalActivitySummary(List<SummaryTranslation> summaryTranslations,
                                                        InstanceStatusType statusType,
                                                        String isoLangCode) {
        return summaryTranslations.stream()
                .filter(trans -> trans.getStatusType().equals(statusType) && trans.getLanguageCode().equals(isoLangCode))
                .map(Translation::getText)
                .findFirst()
                .orElse(null);
    }
}
