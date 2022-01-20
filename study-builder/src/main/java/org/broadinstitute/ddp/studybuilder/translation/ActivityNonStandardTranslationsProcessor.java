package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.addTemplateTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.getTranslationByTemplateVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;

/**
 * Helper class used to copy translations defined in a StudyBuilder activity conf file
 * to the translation properties.<br>
 * Some of {@link ActivityDef} properties defines translations not withing the {@link Template} objects
 * but as a List of {@link Translation}'s.
 * It is the following properties:
 * translatedNames, translatedSecondNames, translatedTitles, translatedSubtitles, translatedDescriptions, translatedSummaries.
 * In order to be possible to define translations for activities the standard way it is possible to define
 * in StudyBuilder conf file the following {@link Template} properties:
 * nameTemplate, secondNameTemplate, titleTemplate, subtitleTemplate, descriptionTemplate, summaryTemplates.
 * Note: property `summaryTemplates` is a map of {@link Template}'s where keys are defined by
 * enum {@link InstanceStatusType}.
 */
public class ActivityNonStandardTranslationsProcessor {

    void run(FormActivityDef activityDef, Map<String, TranslationData> allTranslations) {
        if (activityDef.getNameTemplate() != null) {
            addTemplateTranslations(activityDef.getNameTemplate(), allTranslations);
            activityDef.setTranslatedNames(getTranslationByTemplateVariable(activityDef.getNameTemplate(), allTranslations));
        }

        if (activityDef.getSecondNameTemplate() != null) {
            addTemplateTranslations(activityDef.getSecondNameTemplate(), allTranslations);
            activityDef.setTranslatedSecondNames(getTranslationByTemplateVariable(activityDef.getSecondNameTemplate(), allTranslations));
        }

        if (activityDef.getTitleTemplate() != null) {
            addTemplateTranslations(activityDef.getTitleTemplate(), allTranslations);
            activityDef.setTranslatedTitles(getTranslationByTemplateVariable(activityDef.getTitleTemplate(), allTranslations));
        }

        if (activityDef.getSubtitleTemplate() != null) {
            addTemplateTranslations(activityDef.getSubtitleTemplate(), allTranslations);
            activityDef.setTranslatedSubtitles(getTranslationByTemplateVariable(activityDef.getSubtitleTemplate(), allTranslations));
        }

        if (activityDef.getDescriptionTemplate() != null) {
            addTemplateTranslations(activityDef.getDescriptionTemplate(), allTranslations);
            activityDef.setTranslatedDescriptions(getTranslationByTemplateVariable(activityDef.getDescriptionTemplate(), allTranslations));
        }

        if (activityDef.getSummaryTemplates() != null) {
            List<SummaryTranslation> translatedSummaries = new ArrayList<>();
            activityDef.getSummaryTemplates().forEach((k, v) -> {
                addTemplateTranslations(v, allTranslations);
                List<Translation> templateRendered = getTranslationByTemplateVariable(v, allTranslations);
                if (templateRendered != null) {
                    translatedSummaries.addAll(
                            templateRendered.stream().map(t -> new SummaryTranslation(t.getLanguageCode(), t.getText(), k))
                                    .collect(Collectors.toList())
                    );
                }
            });
            if (translatedSummaries.size() > 0) {
                activityDef.setTranslatedSummaries(translatedSummaries);
            }
        }
    }
}
