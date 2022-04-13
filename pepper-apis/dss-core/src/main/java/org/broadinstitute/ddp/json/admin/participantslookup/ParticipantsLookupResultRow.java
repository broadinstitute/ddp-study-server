package org.broadinstitute.ddp.json.admin.participantslookup;

import com.google.gson.annotations.SerializedName;

/**
 * One participant lookup result data
 */
public class ParticipantsLookupResultRow extends ParticipantsLookupResultRowBase {

    @SerializedName("proxy")
    protected ResultRowBase proxy;

    public ParticipantsLookupResultRow(ResultRowBase resultRowBase) {
        super(resultRowBase);
    }

    public ParticipantsLookupResultRow() {
    }

    public ResultRowBase getProxy() {
        return proxy;
    }

    public void setProxy(ResultRowBase proxy) {
        this.proxy = proxy;
    }
}
