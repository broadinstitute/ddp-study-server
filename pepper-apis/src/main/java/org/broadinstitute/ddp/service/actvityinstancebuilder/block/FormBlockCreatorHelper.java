package org.broadinstitute.ddp.service.actvityinstancebuilder.block;

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
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

public class FormBlockCreatorHelper extends AbstractCreator {

    public FormBlockCreatorHelper(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    ComponentBlock createComponentBlock(ComponentBlockDef componentBlockDef) {
        FormComponent formComponent = null;

        switch (componentBlockDef.getComponentType()) {
            case PHYSICIAN:
                formComponent = new PhysicianComponent(
                        createInstitutionPhysicianComponentDto((PhysicianComponentDef) componentBlockDef),
                        componentBlockDef.shouldHideNumber()
                );
                break;
            case INSTITUTION:
                formComponent = new InstitutionComponent(
                        createInstitutionPhysicianComponentDto((InstitutionComponentDef) componentBlockDef),
                        componentBlockDef.shouldHideNumber()
                );
                break;
            case MAILING_ADDRESS:
                MailingAddressComponentDef mailingAddressComponentDef = (MailingAddressComponentDef) componentBlockDef;
                formComponent = new MailingAddressComponent(
                        renderTemplateIfDefined(mailingAddressComponentDef.getTitleTemplate()),
                        renderTemplateIfDefined(mailingAddressComponentDef.getSubtitleTemplate()),
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

    ConditionalBlock createConditionalBlock(ConditionalBlockDef conditionalBlockDef) {
        ConditionalBlock conditionalBlock = new ConditionalBlock(
                context.getQuestionCreator().createQuestion(conditionalBlockDef.getControl())
        );
        conditionalBlock.getNested().addAll(
                CollectionMiscUtil.createListFromAnotherList(conditionalBlockDef.getNested(),
                    (formBlockDef) -> context.getFormBlockCreator().createBlock(formBlockDef)));
        return conditionalBlock;
    }

    ContentBlock createContentBlock(ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                renderTemplateIfDefined(contentBlockDef.getTitleTemplate()),
                renderTemplateIfDefined(contentBlockDef.getBodyTemplate())
        );
    }

    GroupBlock createGroupBlock(GroupBlockDef groupBlockDef) {
        GroupBlock groupBlock = new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                renderTemplateIfDefined(groupBlockDef.getTitleTemplate())
        );
        groupBlock.getNested().addAll(
                CollectionMiscUtil.createListFromAnotherList(groupBlockDef.getNested(),
                        (formBlockDef) -> context.getFormBlockCreator().createBlock(formBlockDef)));
        return groupBlock;
    }

    NestedActivityBlock createNestedActivityBlock(NestedActivityBlockDef nestedActivityBlockDef) {
        return new NestedActivityBlock(
                nestedActivityBlockDef.getActivityCode(),
                nestedActivityBlockDef.getRenderHint(),
                nestedActivityBlockDef.isAllowMultiple(),
                renderTemplateIfDefined(nestedActivityBlockDef.getAddButtonTemplate())
        );
    }

    QuestionBlock createQuestionBlock(QuestionBlockDef questionBlockDef) {
        return new QuestionBlock(context.getQuestionCreator().createQuestion(questionBlockDef.getQuestion()));
    }

    private InstitutionPhysicianComponentDto createInstitutionPhysicianComponentDto(
            PhysicianInstitutionComponentDef physicianInstitutionComponentDef) {
        return new InstitutionPhysicianComponentDto(
                new ComponentDto(
                        physicianInstitutionComponentDef.getBlockId(),
                        physicianInstitutionComponentDef.getComponentType(),
                        physicianInstitutionComponentDef.shouldHideNumber(),
                        physicianInstitutionComponentDef.getRevisionId()
                ),
                physicianInstitutionComponentDef.getInstitutionType(),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getTitleTemplate()),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getSubtitleTemplate()),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getAddButtonTemplate()),
                physicianInstitutionComponentDef.allowMultiple(),
                physicianInstitutionComponentDef.showFields(),
                physicianInstitutionComponentDef.isRequired()
        );
    }
}
