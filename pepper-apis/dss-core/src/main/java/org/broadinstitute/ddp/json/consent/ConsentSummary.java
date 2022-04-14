package org.broadinstitute.ddp.json.consent;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;

public class ConsentSummary {

    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("instanceGuid")
    private String instanceGuid;

    @SerializedName("consented")
    private Boolean consented;

    @SerializedName("elections")
    private List<ConsentElection> elections = new ArrayList<>();

    private transient String consentedExpr;

    private transient long activityId;

    public ConsentSummary() {
    }

    /**
     * Instantiate ConsentSummary object.
     */
    public ConsentSummary(long activityId, String activityCode, String instanceGuid, String consentedExpr) {
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.instanceGuid = instanceGuid;
        this.consentedExpr = consentedExpr;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }

    public ConsentSummary setInstanceGuid(String instanceGuid) {
        this.instanceGuid = instanceGuid;
        return this;
    }

    public String getConsentedExpr() {
        return consentedExpr;
    }

    public ConsentSummary setConsentedExpr(String consentedExpr) {
        this.consentedExpr = consentedExpr;
        return this;
    }

    public Boolean getConsented() {
        return consented;
    }

    public ConsentSummary setConsented(Boolean consented) {
        this.consented = consented;
        return this;
    }

    public List<ConsentElection> getElections() {
        return elections;
    }

    public ConsentSummary setElections(List<ConsentElection> elections) {
        this.elections = elections;
        return this;
    }

    public long getActivityId() {
        return activityId;
    }

    public void setActivityId(long activityId) {
        this.activityId = activityId;
    }
}
