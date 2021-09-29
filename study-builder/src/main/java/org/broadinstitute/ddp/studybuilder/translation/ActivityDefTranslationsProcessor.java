package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.addTemplateTranslations;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.addTranslations;

import java.util.Map;
import java.util.Properties;

import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.studybuilder.StudyBuilderException;

/**
 * Add the translations (for all study languages) to the activities definitions.
 * Translations can be defined as an array in variables which defined in Templates.
 * Or Translations can be defined as an array in activity definition (like `translatedNames`, `translatedSecondNames`).
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>detect active languages;</li>
 *     <li>process all activity-level templates;</li>
 *     <li>go through all sections and process section level templates;</li>
 *     <li>go through all blocks of a section and process all block level templates (depending on a block type);</li>
 *     <li>for each template do the following steps:
 *       <ol>
 *           <li>if a translation without template (like activityDef.translatedTitles, activityDef.translatedSubtitles)
 *               then process only translations arrays which
 *               are not empty and contain at least one translation (for example with default language):
 *               detect from such element a name of a translation and add translations for the rest of languages
 *               (which are still not defined);</li>
 *           <li>if a template is processed then check it's variables:
 *               - if a variable contains non-empty translation array containing at least one translation
 *                 (for example with default language):
 *                 detect from such element a name of a translation and add translations for the rest of languages
 *                 (which are still not defined);
 *               - if variables array is empty then detect names of variables from a Template text and build
 *                 translations for each of detected variables (for each of active languages).
 *           </li>
 *       </ol>
 *     </li>
 * </ul>
 */
public class ActivityDefTranslationsProcessor {

    private final Map<String, Properties> allTranslations;

    public ActivityDefTranslationsProcessor(Map<String, Properties> allTranslations) {
        this.allTranslations = allTranslations;
    }

    public void enrichActivityDefWithTranslations(ActivityDef activityDef) {
        addTemplateTranslations(activityDef.getReadonlyHintTemplate(), allTranslations);
        activityDef.setTranslatedDescriptions(addTranslations(activityDef.getTranslatedDescriptions(), allTranslations));
        activityDef.setTranslatedNames(addTranslations(activityDef.getTranslatedNames(), allTranslations));
        activityDef.setTranslatedSecondNames(addTranslations(activityDef.getTranslatedSecondNames(), allTranslations));
        activityDef.setTranslatedTitles(addTranslations(activityDef.getTranslatedTitles(), allTranslations));
        activityDef.setTranslatedSubtitles(addTranslations(activityDef.getTranslatedSubtitles(), allTranslations));

        if (activityDef instanceof FormActivityDef) {
            FormActivityDef formDef = (FormActivityDef) activityDef;
            addTemplateTranslations(formDef.getLastUpdatedTextTemplate(), allTranslations);

            enrichSectionWithTranslations(formDef.getIntroduction());
            enrichSectionWithTranslations(formDef.getClosing());
            if (formDef.getSections() != null) {
                formDef.getSections().forEach(s -> enrichSectionWithTranslations(s));
            }
        }
    }

    private void enrichSectionWithTranslations(FormSectionDef sectionDef) {
        if (sectionDef != null) {
            addTemplateTranslations(sectionDef.getNameTemplate(), allTranslations);
            if (sectionDef.getBlocks() != null) {
                sectionDef.getBlocks().forEach(b -> enrichBlockWithTranslations(b));
            }
        }
    }

    private void enrichBlockWithTranslations(FormBlockDef blockDef) {
        switch (blockDef.getBlockType()) {
            case COMPONENT:
                enrichComponentBlockWithTranslations((ComponentBlockDef) blockDef);
                break;
            case CONTENT:
                enrichContentBlockWithTranslations((ContentBlockDef) blockDef);
                break;
            case QUESTION:
                enrichQuestionBlockWithTranslations((QuestionBlockDef) blockDef);
                break;
            case ACTIVITY:
                enrichActivityBlockWithTranslations((NestedActivityBlockDef) blockDef);
                break;
            case CONDITIONAL:
                enrichConditionalBlockWithTranslations((ConditionalBlockDef) blockDef);
                break;
            case GROUP:
                enrichGroupBlockWithTranslations((GroupBlockDef) blockDef);
                break;
            default:
                throw new StudyBuilderException("Unsupported block type specified: " + blockDef.getBlockType());
        }
    }

    private void enrichComponentBlockWithTranslations(ComponentBlockDef blockDef) {
        switch (blockDef.getComponentType()) {
            case MAILING_ADDRESS:
                addTemplateTranslations(((MailingAddressComponentDef) blockDef).getTitleTemplate(), allTranslations);
                addTemplateTranslations(((MailingAddressComponentDef) blockDef).getSubtitleTemplate(), allTranslations);
                break;
            case INSTITUTION:
            case PHYSICIAN:
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getTitleTemplate(), allTranslations);
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getSubtitleTemplate(), allTranslations);
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getAddButtonTemplate(), allTranslations);
                break;
            default:
                throw new StudyBuilderException("Unsupported block type specified: " + blockDef.getBlockType());
        }
    }

    private void enrichContentBlockWithTranslations(ContentBlockDef blockDef) {
        addTemplateTranslations(blockDef.getTitleTemplate(), allTranslations);
        addTemplateTranslations(blockDef.getBodyTemplate(), allTranslations);
    }

    private void enrichActivityBlockWithTranslations(NestedActivityBlockDef blockDef) {
        addTemplateTranslations(blockDef.getAddButtonTemplate(), allTranslations);
    }

    private void enrichConditionalBlockWithTranslations(ConditionalBlockDef blockDef) {
        enrichQuestionWithTranslations(blockDef.getControl());
        if (blockDef.getNested() != null) {
            blockDef.getNested().forEach(b -> enrichBlockWithTranslations(b));
        }
    }

    private void enrichGroupBlockWithTranslations(GroupBlockDef blockDef) {
        addTemplateTranslations(blockDef.getTitleTemplate(), allTranslations);
        if (blockDef.getNested() != null) {
            blockDef.getNested().forEach(b -> enrichBlockWithTranslations(b));
        }
    }

    private void enrichQuestionBlockWithTranslations(QuestionBlockDef blockDef) {
        enrichQuestionWithTranslations(blockDef.getQuestion());
    }

    private void enrichQuestionWithTranslations(QuestionDef questionDef) {
        addTemplateTranslations(questionDef.getPromptTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getTooltipTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getAdditionalInfoHeaderTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getAdditionalInfoFooterTemplate(), allTranslations);
        if (questionDef.getValidations() != null) {
            questionDef.getValidations().forEach(r -> enrichRuleWithTranslations(r));
        }
    }

    private void enrichRuleWithTranslations(RuleDef ruleDef) {
        addTemplateTranslations(ruleDef.getHintTemplate(), allTranslations);
    }
}
