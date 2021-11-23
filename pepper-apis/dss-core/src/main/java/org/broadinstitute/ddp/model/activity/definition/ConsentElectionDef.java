package org.broadinstitute.ddp.model.activity.definition;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;

public class ConsentElectionDef {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @NotBlank
    @SerializedName("selectedExpr")
    private String selectedExpr;

    private transient Long consentElectionId;
    private transient Long selectedExprId;

    public ConsentElectionDef(String stableId, String selectedExpr) {
        this.stableId = stableId;
        this.selectedExpr = selectedExpr;
    }

    public String getStableId() {
        return stableId;
    }

    public String getSelectedExpr() {
        return selectedExpr;
    }

    public Long getConsentElectionId() {
        return consentElectionId;
    }

    public void setConsentElectionId(Long consentElectionId) {
        this.consentElectionId = consentElectionId;
    }

    public Long getSelectedExprId() {
        return selectedExprId;
    }

    public void setSelectedExprId(Long selectedExprId) {
        this.selectedExprId = selectedExprId;
    }
}
