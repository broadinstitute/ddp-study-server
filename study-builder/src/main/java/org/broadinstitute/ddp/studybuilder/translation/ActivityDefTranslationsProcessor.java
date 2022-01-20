package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.addTemplateTranslations;

import java.util.Map;

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
import org.broadinstitute.ddp.model.activity.definition.question.*;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.studybuilder.StudyBuilderException;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add {@link Translation} references (for all study languages) to the {@link Template}s in {@link FormActivityDef}s.
 * An array of {@link Translation} is defined in {@link TemplateVariable} which is defined in a {@link Template}.
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *     <li>detect active languages (from subs.conf or from a folder specified by argument `-i18n-path`);</li>
 *     <li>process all activity-level templates (it's processed the special way:
 *         in a conf can be defined templates for
 *         nameTemplate, secondNameTemplate, titleTemplate, subtitleTemplate, descriptionTemplate, summaryTemplates
 *         and then it's translations (if specified) are copied to existing activity properties:
 *         translatedNames, translatedSecondNames, translatedTitles, translatedSubtitles, translatedDescriptions,
 *         translatedSummaries;</li>
 *     <li>go through all sections and process section level templates;</li>
 *     <li>go through all blocks of a section and process all block level templates (depending on a block type);</li>
 *     <li>process templates for each of questions (depending on type), picklist options, validations..</li>
 *     <li>for each template do the following steps:
 *          - detect list of {@link TemplateVariable}'s: for each of variable create list of translations
 *            (for all of languages which defined in a study).
 *     </li>
 * </ul>
 */
public class ActivityDefTranslationsProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityDefTranslationsProcessor.class);

    private final Map<String, TranslationData> allTranslations;

    public ActivityDefTranslationsProcessor(Map<String, TranslationData> allTranslations) {
        this.allTranslations = allTranslations;
    }

    public void run(FormActivityDef activityDef) {
        if (TranslationsProcessingData.INSTANCE.getTranslationsProcessingType() != null) {
            enrichActivityDefWithTranslations(activityDef);
        }
    }

    private void enrichActivityDefWithTranslations(FormActivityDef activityDef) {
        LOG.info("Add translations for languages {} to a generated activity definition {}",
                TranslationsProcessingData.INSTANCE.getTranslations().keySet(), activityDef.getActivityCode());

        addTemplateTranslations(activityDef.getReadonlyHintTemplate(), allTranslations, true);
        addTemplateTranslations(activityDef.getLastUpdatedTextTemplate(), allTranslations, true);

        new ActivityNonStandardTranslationsProcessor().run(activityDef, allTranslations);

        enrichSectionWithTranslations(activityDef.getIntroduction());
        enrichSectionWithTranslations(activityDef.getClosing());
        if (activityDef.getSections() != null) {
            activityDef.getSections().forEach(this::enrichSectionWithTranslations);
        }
    }

    private void enrichSectionWithTranslations(FormSectionDef sectionDef) {
        if (sectionDef != null) {
            addTemplateTranslations(sectionDef.getNameTemplate(), allTranslations, true);
            if (sectionDef.getBlocks() != null) {
                sectionDef.getBlocks().forEach(this::enrichBlockWithTranslations);
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
                addTemplateTranslations(((MailingAddressComponentDef) blockDef).getTitleTemplate(), allTranslations, true);
                addTemplateTranslations(((MailingAddressComponentDef) blockDef).getSubtitleTemplate(), allTranslations, true);
                break;
            case INSTITUTION:
            case PHYSICIAN:
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getTitleTemplate(), allTranslations, true);
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getSubtitleTemplate(), allTranslations, true);
                addTemplateTranslations(((PhysicianInstitutionComponentDef) blockDef).getAddButtonTemplate(), allTranslations, true);
                break;
            default:
                throw new StudyBuilderException("Unsupported block type specified: " + blockDef.getBlockType());
        }
    }

    private void enrichContentBlockWithTranslations(ContentBlockDef blockDef) {
        addTemplateTranslations(blockDef.getTitleTemplate(), allTranslations, true);
        addTemplateTranslations(blockDef.getBodyTemplate(), allTranslations, true);
    }

    private void enrichActivityBlockWithTranslations(NestedActivityBlockDef blockDef) {
        addTemplateTranslations(blockDef.getAddButtonTemplate(), allTranslations, true);
    }

    private void enrichConditionalBlockWithTranslations(ConditionalBlockDef blockDef) {
        enrichQuestionWithTranslations(blockDef.getControl());
        if (blockDef.getNested() != null) {
            blockDef.getNested().forEach(this::enrichBlockWithTranslations);
        }
    }

    private void enrichGroupBlockWithTranslations(GroupBlockDef blockDef) {
        addTemplateTranslations(blockDef.getTitleTemplate(), allTranslations, true);
        if (blockDef.getNested() != null) {
            blockDef.getNested().forEach(this::enrichBlockWithTranslations);
        }
    }

    private void enrichQuestionBlockWithTranslations(QuestionBlockDef blockDef) {
        enrichQuestionWithTranslations(blockDef.getQuestion());
    }

    private void enrichQuestionWithTranslations(QuestionDef questionDef) {
        addTemplateTranslations(questionDef.getPromptTemplate(), allTranslations, true);
        addTemplateTranslations(questionDef.getTooltipTemplate(), allTranslations, true);
        addTemplateTranslations(questionDef.getAdditionalInfoHeaderTemplate(), allTranslations, true);
        addTemplateTranslations(questionDef.getAdditionalInfoFooterTemplate(), allTranslations, true);
        if (questionDef.getValidations() != null) {
            questionDef.getValidations().forEach(this::enrichRuleWithTranslations);
        }
        switch (questionDef.getQuestionType()) {
            case DATE:
                if (((DateQuestionDef) questionDef).getRenderMode() != PICKLIST) {
                    addTemplateTranslations(((DateQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations, true);
                }
                break;
            case BOOLEAN:
                addTemplateTranslations(((BoolQuestionDef) questionDef).getTrueTemplate(), allTranslations, true);
                addTemplateTranslations(((BoolQuestionDef) questionDef).getFalseTemplate(), allTranslations, true);
                break;
            case TEXT:
                addTemplateTranslations(((TextQuestionDef) questionDef).getConfirmPlaceholderTemplate(), allTranslations, true);
                addTemplateTranslations(((TextQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations, true);
                addTemplateTranslations(((TextQuestionDef) questionDef).getConfirmPromptTemplate(), allTranslations, true);
                addTemplateTranslations(((TextQuestionDef) questionDef).getMismatchMessageTemplate(), allTranslations, true);
                break;
            case NUMERIC:
                addTemplateTranslations(((NumericQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations, true);
                break;
            case PICKLIST:
                addTemplateTranslations(((PicklistQuestionDef) questionDef).getPicklistLabelTemplate(), allTranslations, true);
                ((PicklistQuestionDef) questionDef).getGroups().forEach(this::processPickListOptionGroup);
                ((PicklistQuestionDef) questionDef).getPicklistOptions().forEach(this::processPickListOptionTemplates);
                break;
            case COMPOSITE:
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAddButtonTemplate(), allTranslations, true);
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAdditionalItemTemplate(), allTranslations, true);
                if (((CompositeQuestionDef) questionDef).getChildren() != null) {
                    ((CompositeQuestionDef) questionDef).getChildren().forEach(this::enrichQuestionWithTranslations);
                }
                break;
            case MATRIX:
                ((MatrixQuestionDef) questionDef).getGroups().forEach(MatrixGroupDef::getNameTemplate);
                ((MatrixQuestionDef) questionDef).getOptions().forEach(MatrixOptionDef::getOptionLabelTemplate);
                ((MatrixQuestionDef) questionDef).getOptions().forEach(MatrixOptionDef::getTooltipTemplate);
                ((MatrixQuestionDef) questionDef).getRows().forEach(MatrixRowDef::getRowLabelTemplate);
                ((MatrixQuestionDef) questionDef).getRows().forEach(MatrixRowDef::getTooltipTemplate);
                break;
            case ACTIVITY_INSTANCE_SELECT:
            case AGREEMENT:
            case FILE:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }

    private void processPickListOptionGroup(PicklistGroupDef groupDef) {
        addTemplateTranslations(groupDef.getNameTemplate(), allTranslations, true);

        if (groupDef.getOptions() != null) {
            groupDef.getOptions().forEach(this::processPickListOptionTemplates);
        }
    }

    private void processPickListOptionTemplates(PicklistOptionDef optionDef) {
        addTemplateTranslations(optionDef.getTooltipTemplate(), allTranslations, true);
        addTemplateTranslations(optionDef.getDetailLabelTemplate(), allTranslations, true);
        addTemplateTranslations(optionDef.getOptionLabelTemplate(), allTranslations, true);
        addTemplateTranslations(optionDef.getNestedOptionsLabelTemplate(), allTranslations, true);
        if (optionDef.getNestedOptions() != null) {
            optionDef.getNestedOptions().forEach(this::processPickListOptionTemplates);
        }
    }

    private void enrichRuleWithTranslations(RuleDef ruleDef) {
        addTemplateTranslations(ruleDef.getHintTemplate(), allTranslations, true);
    }
}
