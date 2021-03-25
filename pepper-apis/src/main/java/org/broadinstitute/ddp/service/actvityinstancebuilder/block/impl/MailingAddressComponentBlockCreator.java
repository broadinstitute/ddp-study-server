package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;

/**
 * Creates {@link MailingAddressComponent}
 */
public class MailingAddressComponentBlockCreator extends ElementCreator {

    public MailingAddressComponentBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public MailingAddressComponent createMailingAddressComponent(MailingAddressComponentDef mailingAddressComponentDef) {
        return new MailingAddressComponent(
                renderTemplateIfDefined(mailingAddressComponentDef.getTitleTemplate()),
                renderTemplateIfDefined(mailingAddressComponentDef.getSubtitleTemplate()),
                mailingAddressComponentDef.shouldHideNumber(),
                mailingAddressComponentDef.shouldRequireVerified(),
                mailingAddressComponentDef.shouldRequirePhone()
        );
    }
}
