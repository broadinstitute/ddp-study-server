package org.broadinstitute.ddp.model.activity.instance;

import java.util.function.Consumer;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public class MailingAddressComponent extends FormComponent {

    @SerializedName("parameters")
    private SerializedFields serializedFields;

    public MailingAddressComponent(String titleText, String subtitleText, boolean hideNumber) {
        super(ComponentType.MAILING_ADDRESS);
        hideDisplayNumber = hideNumber;
        serializedFields = new SerializedFields(titleText, subtitleText);
    }

    public static class SerializedFields {
        @SerializedName("titleText")
        private static String titleText;
        @SerializedName("subtitleText")
        private static String subtitleText;

        public SerializedFields(String titleText, String subtitleText) {
            this.subtitleText = subtitleText;
            this.titleText = titleText;
        }
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        // Implement this method once we decide to use bulk rendering for this component
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        // Implement this method once we decide to use bulk rendering for this component
    }
}
