package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;

import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.instance.ComponentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;

/**
 * Creates {@link ComponentBlock}
 */
public class ComponentBlockCreator extends ElementCreator {

    public ComponentBlockCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public ComponentBlock createComponentBlock(ComponentBlockDef componentBlockDef) {
        FormComponent formComponent = null;

        switch (componentBlockDef.getComponentType()) {
            case PHYSICIAN:
                formComponent = new PhysicianInstitutionComponentBlockCreator(context).createPhysicianComponent(
                        componentBlockDef);
                break;
            case INSTITUTION:
                formComponent = new PhysicianInstitutionComponentBlockCreator(context).createInstitutionComponent(
                        componentBlockDef);
                break;
            case MAILING_ADDRESS:
                formComponent = new MailingAddressComponentBlockCreator(context).createMailingAddressComponent(
                        (MailingAddressComponentDef) componentBlockDef);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + componentBlockDef.getComponentType());
        }

        return new ComponentBlock(formComponent);
    }
}
