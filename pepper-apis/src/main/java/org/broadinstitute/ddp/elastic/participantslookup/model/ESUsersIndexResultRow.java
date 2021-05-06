package org.broadinstitute.ddp.elastic.participantslookup.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.admin.participantslookup.ResultRowBase;

/**
 * Result row fetched during reading from ES index "users".
 */
public class ESUsersIndexResultRow extends ResultRowBase {

    @SerializedName("governedUsers")
    protected List<String> governedUsers = new ArrayList<>();

    public ESUsersIndexResultRow() {}

    public ESUsersIndexResultRow(ResultRowBase resultRowBase) {
        super(resultRowBase);
    }

    public List<String> getGovernedUsers() {
        return governedUsers;
    }

    public void setGovernedUsers(List<String> governedUsers) {
        this.governedUsers = governedUsers;
    }
}
