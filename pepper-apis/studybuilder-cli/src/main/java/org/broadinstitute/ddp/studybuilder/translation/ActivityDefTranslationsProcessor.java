package org.broadinstitute.ddp.studybuilder.translation;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;
import static org.broadinstitute.ddp.studybuilder.translation.TranslationsEnricher.addTemplateTranslations;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
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
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.studybuilder.StudyBuilderException;
import org.broadinstitute.ddp.studybuilder.translation.TranslationsProcessingData.TranslationData;

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
@Slf4j
public class ActivityDefTranslationsProcessor {
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
        log.info("Add translations for languages {} to a generated activity definition {}",
                TranslationsProcessingData.INSTANCE.getTranslations().keySet(), activityDef.getActivityCode());

        addTemplateTranslations(activityDef.getReadonlyHintTemplate(), allTranslations);
        addTemplateTranslations(activityDef.getLastUpdatedTextTemplate(), allTranslations);

        new ActivityNonStandardTranslationsProcessor().run(activityDef, allTranslations);

        enrichSectionWithTranslations(activityDef.getIntroduction());
        enrichSectionWithTranslations(activityDef.getClosing());
        if (activityDef.getSections() != null) {
            activityDef.getSections().forEach(this::enrichSectionWithTranslations);
        }
    }

    private void enrichSectionWithTranslations(FormSectionDef sectionDef) {
        if (sectionDef != null) {
            addTemplateTranslations(sectionDef.getNameTemplate(), allTranslations);
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
            case TABULAR:
                enrichTabularBlockWithTranslations((TabularBlockDef) blockDef);
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
            blockDef.getNested().forEach(this::enrichBlockWithTranslations);
        }
    }

    private void enrichTabularBlockWithTranslations(TabularBlockDef blockDef) {
        StreamEx.of(blockDef.getHeaders())
                .map(TabularHeaderDef::getLabel)
                .forEach(label -> addTemplateTranslations(label, allTranslations));

        StreamEx.of(blockDef.getQuestions()).forEach(this::enrichQuestionWithTranslations);
    }

    private void enrichGroupBlockWithTranslations(GroupBlockDef blockDef) {
        addTemplateTranslations(blockDef.getTitleTemplate(), allTranslations);
        if (blockDef.getNested() != null) {
            blockDef.getNested().forEach(this::enrichBlockWithTranslations);
        }
    }

    private void enrichQuestionBlockWithTranslations(QuestionBlockDef blockDef) {
        enrichQuestionWithTranslations(blockDef.getQuestion());
    }

    private void enrichPickListOptionGroup(PicklistGroupDef groupDef) {
        addTemplateTranslations(groupDef.getNameTemplate(), allTranslations);

        if (groupDef.getOptions() != null) {
            groupDef.getOptions().forEach(this::enrichPickListOptionTemplates);
        }
    }

    private void enrichPickListOptionTemplates(PicklistOptionDef optionDef) {
        addTemplateTranslations(optionDef.getTooltipTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getDetailLabelTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getOptionLabelTemplate(), allTranslations);
        addTemplateTranslations(optionDef.getNestedOptionsLabelTemplate(), allTranslations);
        if (optionDef.getNestedOptions() != null) {
            optionDef.getNestedOptions().forEach(this::enrichPickListOptionTemplates);
        }
    }

    private void enrichRuleWithTranslations(RuleDef ruleDef) {
        addTemplateTranslations(ruleDef.getHintTemplate(), allTranslations);
    }

    private void enrichMatrixGroupWithTranslations(MatrixGroupDef matrixGroupDef) {
        addTemplateTranslations(matrixGroupDef.getNameTemplate(), allTranslations);
    }

    private void enrichMatrixGroupWithTranslations(MatrixOptionDef matrixOptionDef) {
        addTemplateTranslations(matrixOptionDef.getOptionLabelTemplate(), allTranslations);
        addTemplateTranslations(matrixOptionDef.getTooltipTemplate(), allTranslations);
    }

    private void enrichMatrixOptionWithTranslations(MatrixRowDef matrixRowDef) {
        addTemplateTranslations(matrixRowDef.getRowLabelTemplate(), allTranslations);
        addTemplateTranslations(matrixRowDef.getTooltipTemplate(), allTranslations);
    }

    private void enrichQuestionWithTranslations(QuestionDef questionDef) {
        addTemplateTranslations(questionDef.getPromptTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getTooltipTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getAdditionalInfoHeaderTemplate(), allTranslations);
        addTemplateTranslations(questionDef.getAdditionalInfoFooterTemplate(), allTranslations);

        if (questionDef.getValidations() != null) {
            questionDef.getValidations().forEach(this::enrichRuleWithTranslations);
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
            case DECIMAL:
                addTemplateTranslations(((DecimalQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations);
                break;
            case EQUATION:
                addTemplateTranslations(((EquationQuestionDef) questionDef).getPlaceholderTemplate(), allTranslations);
                break;
            case PICKLIST:
                addTemplateTranslations(((PicklistQuestionDef) questionDef).getPicklistLabelTemplate(), allTranslations);
                ((PicklistQuestionDef) questionDef).getGroups().forEach(this::enrichPickListOptionGroup);
                ((PicklistQuestionDef) questionDef).getPicklistOptions().forEach(this::enrichPickListOptionTemplates);
                break;
            case COMPOSITE:
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAddButtonTemplate(), allTranslations);
                addTemplateTranslations(((CompositeQuestionDef) questionDef).getAdditionalItemTemplate(), allTranslations);
                if (((CompositeQuestionDef) questionDef).getChildren() != null) {
                    ((CompositeQuestionDef) questionDef).getChildren().forEach(this::enrichQuestionWithTranslations);
                }
                break;
            case MATRIX:
                addTemplateTranslations(((MatrixQuestionDef) questionDef).getModalTemplate(), allTranslations);
                addTemplateTranslations(((MatrixQuestionDef) questionDef).getModalTitleTemplate(), allTranslations);
                ((MatrixQuestionDef) questionDef).getGroups().forEach(this::enrichMatrixGroupWithTranslations);
                ((MatrixQuestionDef) questionDef).getOptions().forEach(this::enrichMatrixGroupWithTranslations);
                ((MatrixQuestionDef) questionDef).getRows().forEach(this::enrichMatrixOptionWithTranslations);
                break;
            case ACTIVITY_INSTANCE_SELECT:
            case AGREEMENT:
            case FILE:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }
}
