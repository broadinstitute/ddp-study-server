package org.broadinstitute.ddp.elastic.participantslookup.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;

/**
 * Result row fetched during reading from ES index "users".
 */
public class ESUsersIndexResultRow extends ParticipantsLookupResultRowBase {

    @SerializedName("governedUsers")
    protected List<String> governedUsers = new ArrayList<>();

    public ESUsersIndexResultRow() {}

    public ESUsersIndexResultRow(ParticipantsLookupResultRowBase resultRowBase) {
        super(resultRowBase);
    }

    public List<String> getGovernedUsers() {
        return governedUsers;
    }

    public void setGovernedUsers(List<String> governedUsers) {
        this.governedUsers = governedUsers;
    }
}
