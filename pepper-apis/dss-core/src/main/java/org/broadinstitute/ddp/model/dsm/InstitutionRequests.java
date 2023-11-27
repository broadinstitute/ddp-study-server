package org.broadinstitute.ddp.model.dsm;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class InstitutionRequests {
    @SerializedName("id")
    long lastModifiedEpoch;

    @SerializedName("participantId")
    String userGuid;

    @SerializedName("lastUpdated")
    String lastModifiedUTC;

    @SerializedName("institutions")
    List<Institution> institutions;

    public InstitutionRequests(long lastModifiedEpoch,
                               String userGuid,
                               String lastModifiedUTC,
                               List<Institution> institutions) {
        this.lastModifiedEpoch = lastModifiedEpoch;
        this.userGuid = userGuid;
        this.lastModifiedUTC = lastModifiedUTC;
        this.institutions = institutions;
    }

    public long getLastModifiedEpoch() {
        return lastModifiedEpoch;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getLastModifiedUTC() {
        return lastModifiedUTC;
    }

    public List<Institution> getInstitutions() {
        return institutions;
    }
}
