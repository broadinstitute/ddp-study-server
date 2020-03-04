package org.broadinstitute.ddp.model.activity.instance;

import com.google.gson.annotations.SerializedName;

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
}
