package org.broadinstitute.ddp.model.activity.definition;

import javax.validation.Valid;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public final class MailingAddressComponentDef extends ComponentBlockDef {

    @Valid
    @SerializedName("titleTemplate")
    private Template titleTemplate;

    @Valid
    @SerializedName("subtitleTemplate")
    private Template subtitleTemplate;

    @SerializedName("requireVerified")
    private boolean requireVerified;

    @SerializedName("requirePhone")
    private boolean requirePhone;

    public MailingAddressComponentDef(Template titleTemplate, Template subtitleTemplate) {
        super(ComponentType.MAILING_ADDRESS);
        this.titleTemplate = titleTemplate;
        this.subtitleTemplate = subtitleTemplate;
    }

    public Template getTitleTemplate() {
        return titleTemplate;
    }

    public Template getSubtitleTemplate() {
        return subtitleTemplate;
    }

    public boolean shouldRequireVerified() {
        return requireVerified;
    }

    public void setRequireVerified(boolean requireVerified) {
        this.requireVerified = requireVerified;
    }

    public boolean shouldRequirePhone() {
        return requirePhone;
    }

    public void setRequirePhone(boolean requirePhone) {
        this.requirePhone = requirePhone;
    }
}
