package org.broadinstitute.ddp.service.actvityinstancebuilder.block;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.util.TemplateHandler.addAndRenderTemplate;

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
import org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class FormBlockCreatorHelper {

    ComponentBlock createComponentBlock(AIBuilderContext ctx, ComponentBlockDef componentBlockDef) {
        FormComponent formComponent = null;

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
                        addAndRenderTemplate(ctx, mailingAddressComponentDef.getTitleTemplate()),
                        addAndRenderTemplate(ctx, mailingAddressComponentDef.getSubtitleTemplate()),
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
        ConditionalBlock conditionalBlock = new ConditionalBlock(
                ctx.creators().getQuestionCreator().createQuestion(ctx, conditionalBlockDef.getControl())
        );
        conditionalBlock.getNested().addAll(
                CollectionMiscUtil.createListFromAnotherList(conditionalBlockDef.getNested(),
                    (formBlockDef) -> ctx.creators().getFormBlockCreator().createBlock(ctx, formBlockDef)));
        return conditionalBlock;
    }

    ContentBlock createContentBlock(AIBuilderContext ctx, ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                addAndRenderTemplate(ctx, contentBlockDef.getTitleTemplate()),
                addAndRenderTemplate(ctx, contentBlockDef.getBodyTemplate())
        );
    }

    GroupBlock createGroupBlock(AIBuilderContext ctx, GroupBlockDef groupBlockDef) {
        GroupBlock groupBlock = new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                addAndRenderTemplate(ctx, groupBlockDef.getTitleTemplate())
        );
        groupBlock.getNested().addAll(
                CollectionMiscUtil.createListFromAnotherList(groupBlockDef.getNested(),
                        (formBlockDef) -> ctx.creators().getFormBlockCreator().createBlock(ctx, formBlockDef)));
        return groupBlock;
    }

    NestedActivityBlock createNestedActivityBlock(AIBuilderContext ctx, NestedActivityBlockDef nestedActivityBlockDef) {
        return new NestedActivityBlock(
                nestedActivityBlockDef.getActivityCode(),
                nestedActivityBlockDef.getRenderHint(),
                nestedActivityBlockDef.isAllowMultiple(),
                addAndRenderTemplate(ctx, nestedActivityBlockDef.getAddButtonTemplate())
        );
    }

    QuestionBlock createQuestionBlock(AIBuilderContext ctx, QuestionBlockDef questionBlockDef) {
        return new QuestionBlock(ctx.creators().getQuestionCreator().createQuestion(ctx, questionBlockDef.getQuestion()));
    }

    private InstitutionPhysicianComponentDto createInstitutionPhysicianComponentDto(
            AIBuilderContext ctx,
            PhysicianInstitutionComponentDef physicianInstitutionComponentDef) {
        return new InstitutionPhysicianComponentDto(
                new ComponentDto(
                        physicianInstitutionComponentDef.getBlockId(),
                        physicianInstitutionComponentDef.getComponentType(),
                        physicianInstitutionComponentDef.shouldHideNumber(),
                        physicianInstitutionComponentDef.getRevisionId()
                ),
                physicianInstitutionComponentDef.getInstitutionType(),
                addAndRenderTemplate(ctx, physicianInstitutionComponentDef.getTitleTemplate()),
                addAndRenderTemplate(ctx, physicianInstitutionComponentDef.getSubtitleTemplate()),
                addAndRenderTemplate(ctx, physicianInstitutionComponentDef.getAddButtonTemplate()),
                physicianInstitutionComponentDef.allowMultiple(),
                physicianInstitutionComponentDef.showFields(),
                physicianInstitutionComponentDef.isRequired()
        );
    }
}
