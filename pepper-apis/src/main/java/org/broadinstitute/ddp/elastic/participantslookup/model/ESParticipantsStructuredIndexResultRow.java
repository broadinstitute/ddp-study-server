package org.broadinstitute.ddp.elastic.participantslookup.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;

/**
 * Result row fetched during reading from ES index "participants_structures".
 */
public class ESParticipantsStructuredIndexResultRow extends ParticipantsLookupResultRowBase {

    @SerializedName("proxies")
    protected List<String> proxies = new ArrayList<>();

    public ESParticipantsStructuredIndexResultRow() {}

    public List<String> getProxies() {
        return proxies;
    }

    public void setProxies(List<String> proxies) {
        this.proxies = proxies;
    }
}
