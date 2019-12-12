package org.broadinstitute.ddp.model.dsm;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class DrugSuggestion {
    @SerializedName("drug")
    private Drug drug;
    @SerializedName("matches")
    private List<PatternMatch> matches;

    public DrugSuggestion(Drug drug, List<PatternMatch> matches) {
        this.drug = drug;
        this.matches = matches;
    }

    public Drug getDrug() {
        return drug;
    }

    public List<PatternMatch> getMatches() {
        return matches;
    }
}
