package org.broadinstitute.ddp.migration;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Represents the migration source file containing mailing list data.
 */
class MailingListFile {

    @SerializedName("mailing_list_data")
    private List<MailingListContact> contacts = new ArrayList<>();

    public List<MailingListContact> getContacts() {
        return contacts;
    }
}
