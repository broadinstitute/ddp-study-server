package org.broadinstitute.ddp.export.json.structured;

import java.util.Set;

import com.google.gson.annotations.SerializedName;

public class UserRecord {

    @SerializedName("profile")
    private ParticipantProfile profile;
    @SerializedName("proxies")
    private Set<String> proxies;
    @SerializedName("governedUsers")
    private Set<String> governedUsers;

    public UserRecord(ParticipantProfile profile, Set<String> proxies, Set<String> governedUsers) {
        this.profile = profile;
        this.proxies = proxies;
        this.governedUsers = governedUsers;
    }
}
