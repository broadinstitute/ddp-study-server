package org.broadinstitute.ddp.model.suggestion;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.dsm.Cancer;

public class CancerSuggestion {
    @SerializedName("cancer")
    private Cancer cancer;
    @SerializedName("matches")
    private List<PatternMatch> matches;

    public CancerSuggestion(Cancer cancer, List<PatternMatch> matches) {
        this.cancer = cancer;
        this.matches = matches;
    }

    public Cancer getCancer() {
        return cancer;
    }

    public List<PatternMatch> getMatches() {
        return matches;
    }
}
