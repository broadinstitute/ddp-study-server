package org.broadinstitute.ddp.migration;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a single mailing-list contact to be imported.
 */
class MailingListContact {

    @SerializedName("firstname")
    private String firstName;
    @SerializedName("lastname")
    private String lastName;
    @SerializedName("email")
    private String email;
    @SerializedName("info")
    private String info;
    @SerializedName("datecreated")
    private long dateCreatedMillis;

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getInfo() {
        return info;
    }

    public long getDateCreatedMillis() {
        return dateCreatedMillis;
    }
}
