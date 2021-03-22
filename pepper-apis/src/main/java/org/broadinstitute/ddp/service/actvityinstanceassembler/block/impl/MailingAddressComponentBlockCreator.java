package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link MailingAddressComponent}
 */
public class MailingAddressComponentBlockCreator extends ElementCreator {

    public MailingAddressComponentBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public MailingAddressComponent createMailingAddressComponent(MailingAddressComponentDef mailingAddressComponentDef) {
        MailingAddressComponent mailingAddressComponent = constructMailingAddressComponent(mailingAddressComponentDef);
        render(mailingAddressComponent, mailingAddressComponentDef);
        return mailingAddressComponent;
    }

    private MailingAddressComponent constructMailingAddressComponent(MailingAddressComponentDef mailingAddressComponentDef) {
        return new MailingAddressComponent(
                mailingAddressComponentDef.getTitleTemplate() != null
                        ? mailingAddressComponentDef.getTitleTemplate().getTemplateId() : null,
                mailingAddressComponentDef.getSubtitleTemplate() != null
                        ? mailingAddressComponentDef.getSubtitleTemplate().getTemplateId() : null,
                mailingAddressComponentDef.shouldHideNumber(),
                mailingAddressComponentDef.shouldRequireVerified(),
                mailingAddressComponentDef.shouldRequirePhone()
        );
    }

    private void render(MailingAddressComponent mailingAddressComponent, MailingAddressComponentDef mailingAddressComponentDef) {
        renderTemplate(mailingAddressComponentDef.getTitleTemplate(),
                mailingAddressComponentDef.getTitleTemplate().getTemplateId(), mailingAddressComponent, context);
        renderTemplate(mailingAddressComponentDef.getSubtitleTemplate(),
                mailingAddressComponentDef.getSubtitleTemplate().getTemplateId(), mailingAddressComponent, context);
    }
}
