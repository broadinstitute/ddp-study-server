package org.broadinstitute.lddp.email;

import com.google.gson.annotations.SerializedName;

/**
 * Simple tuple class that keeps track of
 * information about the sender of an email
 * when using sendgrid.
 */
public class EmailSender {

    @SerializedName("sendGridFrom")
    private String fromEmailAddress;

    @SerializedName("sendGridFromName")
    private String fromName;

    public EmailSender(String fromEmailAddress, String fromName) {
        this.fromEmailAddress = fromEmailAddress;
        this.fromName = fromName;
    }

    public String getFromEmailAddress() {
        return fromEmailAddress;
    }

    public String getFromName() {
        return fromName;
    }
}
