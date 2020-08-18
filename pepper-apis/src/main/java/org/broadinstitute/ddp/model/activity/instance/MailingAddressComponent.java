package org.broadinstitute.ddp.model.activity.instance;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public class MailingAddressComponent extends FormComponent {

    @SerializedName("parameters")
    private SerializedFields serializedFields;

    private transient Long titleTemplateId;
    private transient Long subtitleTemplateId;

    public MailingAddressComponent(Long titleTemplateId, Long subtitleTemplateId, boolean hideNumber,
                                   boolean requireVerified, boolean requirePhone) {
        super(ComponentType.MAILING_ADDRESS);
        hideDisplayNumber = hideNumber;
        this.titleTemplateId = titleTemplateId;
        this.subtitleTemplateId = subtitleTemplateId;
        serializedFields = new SerializedFields();
        serializedFields.setRequireVerified(requireVerified);
        serializedFields.setRequirePhone(requirePhone);
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        if (titleTemplateId != null) {
            registry.accept(titleTemplateId);
        }
        if (subtitleTemplateId != null) {
            registry.accept(subtitleTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        if (titleTemplateId != null) {
            String text = rendered.get(titleTemplateId);
            if (text == null) {
                String msg = "No rendered template found for mailing address component title with id " + titleTemplateId;
                throw new NoSuchElementException(msg);
            }
            if (style == ContentStyle.BASIC) {
                text = HtmlConverter.getPlainText(text);
            }
            serializedFields.setTitleText(text);
        }
        if (subtitleTemplateId != null) {
            String text = rendered.get(subtitleTemplateId);
            if (text == null) {
                String msg = "No rendered template found for mailing address component subtitle with id " + subtitleTemplateId;
                throw new NoSuchElementException(msg);
            }
            if (style == ContentStyle.BASIC) {
                text = HtmlConverter.getPlainText(text);
            }
            serializedFields.setSubtitleText(text);
        }
    }

    public boolean shouldRequireVerified() {
        return serializedFields.requireVerified;
    }

    public boolean shouldRequirePhone() {
        return serializedFields.requirePhone;
    }

    public static class SerializedFields {
        @SerializedName("titleText")
        private String titleText;
        @SerializedName("subtitleText")
        private String subtitleText;
        @SerializedName("requireVerified")
        private boolean requireVerified;
        @SerializedName("requirePhone")
        private boolean requirePhone;

        public void setTitleText(String titleText) {
            this.titleText = titleText;
        }

        public void setSubtitleText(String subtitleText) {
            this.subtitleText = subtitleText;
        }

        public void setRequireVerified(boolean requireVerified) {
            this.requireVerified = requireVerified;
        }

        public void setRequirePhone(boolean requirePhone) {
            this.requirePhone = requirePhone;
        }
    }
}
