package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

/**
 * Creates {@link MailingAddressComponent}
 */
public class MailingAddressComponentBlockCreator extends ElementCreator {

    public MailingAddressComponentBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public MailingAddressComponent createMailingAddressComponent(MailingAddressComponentDef mailingAddressComponentDef) {
        return new MailingAddressComponent(
                getTemplateId(mailingAddressComponentDef.getTitleTemplate()),
                getTemplateId(mailingAddressComponentDef.getSubtitleTemplate()),
                mailingAddressComponentDef.shouldHideNumber(),
                mailingAddressComponentDef.shouldRequireVerified(),
                mailingAddressComponentDef.shouldRequirePhone()
        );
    }
}
