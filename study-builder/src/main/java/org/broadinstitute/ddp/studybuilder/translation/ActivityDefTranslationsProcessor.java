package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;
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
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.studybuilder.StudyBuilderContext;
import org.broadinstitute.ddp.studybuilder.StudyBuilderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add {@link Translation} references (for all study languages) to the {@link Template}s in {@link FormActivityDef}s.
 * An array of {@link Translation} is defined in {@link TemplateVariable} which is defined in a {@link Template}.
 * Another translations definition: {@link Translation}s can be defined as an array directly in {@link FormActivityDef}
 * (properties like `translatedNames`, `translatedSecondNames`).
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>detect active languages (from subs.conf or from a folder specified by argument `-i18n-path`);</li>
 *     <li>process all activity-level templates;</li>
 *     <li>go through all sections and process section level templates;</li>
 *     <li>go through all blocks of a section and process all block level templates (depending on a block type);</li>
 *     <li>process templates for each of questions (depending on type), picklist options, validations..</li>
 *     <li>for each template do the following steps:
 *       <ol>
 *           <li>if a list of {@link Translation} without {@link Template} (like activityDef.translatedTitles,
 *               activityDef.translatedSubtitles) then process only translations arrays which
 *               are not empty and contain at least one translation where instead of a text defined a translation kes
 *               (for example `$prequal.name`): using this key generate translations for all languages specified in
 *               a study (except for translations which already defined and which not contain translation keys);</li>
 *           <li>if a {@link Template} is processed then check it's {@link TemplateVariable}:
 *               - if a {@link TemplateVariable} contains non-empty array {@link Translation} containing at least one translation
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

    private static final Logger LOG = LoggerFactory.getLogger(ActivityDefTranslationsProcessor.class);

    private final Map<String, Properties> allTranslations;

    public ActivityDefTranslationsProcessor(Map<String, Properties> allTranslations) {
        this.allTranslations = allTranslations;
    }

    public void run(ActivityDef activityDef) {
        if (StudyBuilderContext.CONTEXT.isProcessTranslations()) {
            enrichActivityDefWithTranslations(activityDef);
        }
    }

    private void enrichActivityDefWithTranslations(ActivityDef activityDef) {
        LOG.info("Add translations for languages {} to a generated activity definition {}",
                StudyBuilderContext.CONTEXT.getTranslations().keySet(), activityDef.getActivityCode());

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
        switch (questionDef.getQuestionType()) {
            case DATE:
                if (((DateQuestionDef) questionDef).getRenderMode() != PICKLIST) {
                    addTemplateTranslations(((DateQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations);
                }
                break;
            case BOOLEAN:
                addTemplateTranslations(((BoolQuestionDef) questionDef).getTrueTemplate(), allTranslations);
                addTemplateTranslations(((BoolQuestionDef) questionDef).getFalseTemplate(), allTranslations);
                break;
            case TEXT:
                addTemplateTranslations(((TextQuestionDef) questionDef).getConfirmPlaceholderTemplate(), allTranslations);
                addTemplateTranslations(((TextQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations);
                addTemplateTranslations(((TextQuestionDef) questionDef).getConfirmPromptTemplate(), allTranslations);
                addTemplateTranslations(((TextQuestionDef) questionDef).getMismatchMessageTemplate(), allTranslations);
                break;
            case NUMERIC:
                addTemplateTranslations(((NumericQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations);
                break;
            case PICKLIST:
                addTemplateTranslations(((PicklistQuestionDef) questionDef).getPicklistLabelTemplate(), allTranslations);
                ((PicklistQuestionDef) questionDef).getGroups().forEach(
                        g -> addTemplateTranslations(g.getNameTemplate(), allTranslations));
                ((PicklistQuestionDef) questionDef).getPicklistOptions().forEach(o -> processPickListOptionTemplates(o));
                break;
            case COMPOSITE:
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAddButtonTemplate(), allTranslations);
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAdditionalItemTemplate(), allTranslations);
                if (((CompositeQuestionDef) questionDef).getChildren() != null) {
                    ((CompositeQuestionDef) questionDef).getChildren().forEach(q -> enrichQuestionWithTranslations(q));
                }
                break;
            case AGREEMENT:
            case FILE:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }

    private void processPickListOptionTemplates(PicklistOptionDef optionDef) {
        addTemplateTranslations(optionDef.getTooltipTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getDetailLabelTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getOptionLabelTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getNestedOptionsLabelTemplate(), allTranslations);
        if (optionDef.getNestedOptions() != null) {
            optionDef.getNestedOptions().forEach(n -> processPickListOptionTemplates(n));
        }
    }

    private void enrichRuleWithTranslations(RuleDef ruleDef) {
        addTemplateTranslations(ruleDef.getHintTemplate(), allTranslations);
    }
}
