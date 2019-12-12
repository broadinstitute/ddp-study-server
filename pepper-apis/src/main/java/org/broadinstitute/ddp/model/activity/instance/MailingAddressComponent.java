package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.model.activity.types.ComponentType;

public class MailingAddressComponent extends FormComponent {

    public static final String TITLE_TEXT = "titleText";
    public static final String SUBTITLE_TEXT = "subtitleText";

    public MailingAddressComponent(String titleText, String subtitleText, boolean hideNumber) {
        super(ComponentType.MAILING_ADDRESS);
        hideDisplayNumber = hideNumber;
        parameters.put(TITLE_TEXT, titleText);
        parameters.put(SUBTITLE_TEXT, subtitleText);
    }
}
