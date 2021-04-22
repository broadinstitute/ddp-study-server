package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block;

import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.ComponentBlock;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.instance.InstitutionComponent;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
import org.broadinstitute.ddp.model.activity.instance.PhysicianComponent;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class FormBlockCreatorHelper {

    ComponentBlock createComponentBlock(AIBuilderContext ctx, ComponentBlockDef componentBlockDef) {
        FormComponent formComponent;

        switch (componentBlockDef.getComponentType()) {
            case PHYSICIAN:
                formComponent = new PhysicianComponent(
                        createInstitutionPhysicianComponentDto(ctx, (PhysicianComponentDef) componentBlockDef),
                        componentBlockDef.shouldHideNumber()
                );
                break;
            case INSTITUTION:
                formComponent = new InstitutionComponent(
                        createInstitutionPhysicianComponentDto(ctx, (InstitutionComponentDef) componentBlockDef),
                        componentBlockDef.shouldHideNumber()
                );
                break;
            case MAILING_ADDRESS:
                MailingAddressComponentDef mailingAddressComponentDef = (MailingAddressComponentDef) componentBlockDef;
                formComponent = new MailingAddressComponent(
                        ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                                ctx, mailingAddressComponentDef.getTitleTemplate()),
                        ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                                ctx, mailingAddressComponentDef.getSubtitleTemplate()),
                        mailingAddressComponentDef.shouldHideNumber(),
                        mailingAddressComponentDef.shouldRequireVerified(),
                        mailingAddressComponentDef.shouldRequirePhone()
                );
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + componentBlockDef.getComponentType());
        }

        return new ComponentBlock(formComponent);
    }

    ConditionalBlock createConditionalBlock(AIBuilderContext ctx, ConditionalBlockDef conditionalBlockDef) {
        Question question = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator().createQuestion(
                ctx, conditionalBlockDef.getControl());
        ConditionalBlock conditionalBlock = question == null ? null : new ConditionalBlock(question);
        if (conditionalBlock != null) {
            conditionalBlock.getNested().addAll(
                    CollectionMiscUtil.createListFromAnotherList(conditionalBlockDef.getNested(),
                            (formBlockDef) -> ctx.getAIBuilderFactory().getAICreatorsFactory().getFormBlockCreator()
                                    .createBlock(ctx, formBlockDef)));
        }
        return conditionalBlock;
    }

    ContentBlock createContentBlock(AIBuilderContext ctx, ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(ctx, contentBlockDef.getTitleTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(ctx, contentBlockDef.getBodyTemplate())
        );
    }

    GroupBlock createGroupBlock(AIBuilderContext ctx, GroupBlockDef groupBlockDef) {
        GroupBlock groupBlock = new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, groupBlockDef.getTitleTemplate())
        );
        groupBlock.getNested().addAll(
                CollectionMiscUtil.createListFromAnotherList(groupBlockDef.getNested(),
                        (formBlockDef) -> ctx.getAIBuilderFactory().getAICreatorsFactory().getFormBlockCreator()
                                .createBlock(ctx, formBlockDef)));
        return groupBlock;
    }

    NestedActivityBlock createNestedActivityBlock(AIBuilderContext ctx, NestedActivityBlockDef nestedActivityBlockDef) {
        return new NestedActivityBlock(
                nestedActivityBlockDef.getActivityCode(),
                nestedActivityBlockDef.getRenderHint(),
                nestedActivityBlockDef.isAllowMultiple(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, nestedActivityBlockDef.getAddButtonTemplate())
        );
    }

    QuestionBlock createQuestionBlock(AIBuilderContext ctx, QuestionBlockDef questionBlockDef) {
        Question question = ctx.getAIBuilderFactory().getAICreatorsFactory().getQuestionCreator()
                .createQuestion(ctx, questionBlockDef.getQuestion());
        return question == null ? null : new QuestionBlock(question);
    }

    private InstitutionPhysicianComponentDto createInstitutionPhysicianComponentDto(
            AIBuilderContext ctx,
            PhysicianInstitutionComponentDef physicianInstitutionComponentDef) {
        return new InstitutionPhysicianComponentDto(
                new ComponentDto(
                        physicianInstitutionComponentDef.getBlockId(),
                        physicianInstitutionComponentDef.getComponentType(),
                        physicianInstitutionComponentDef.shouldHideNumber(),
                        physicianInstitutionComponentDef.getComponentRevisionId()
                ),
                physicianInstitutionComponentDef.getInstitutionType(),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, physicianInstitutionComponentDef.getTitleTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, physicianInstitutionComponentDef.getSubtitleTemplate()),
                ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(
                        ctx, physicianInstitutionComponentDef.getAddButtonTemplate()),
                physicianInstitutionComponentDef.allowMultiple(),
                physicianInstitutionComponentDef.showFields(),
                physicianInstitutionComponentDef.isRequired()
        );
    }
}
