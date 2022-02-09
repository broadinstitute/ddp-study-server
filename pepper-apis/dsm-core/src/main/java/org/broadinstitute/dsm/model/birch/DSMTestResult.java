package org.broadinstitute.dsm.model.birch;

import lombok.Data;

@Data
public class DSMTestResult {
    public boolean isCorrected;
    public String result;
    public String timeCompleted;

    public DSMTestResult(String result, String timeCompleted, boolean isCorrected) {
        this.result = result;
        this.timeCompleted = timeCompleted;
        this.isCorrected = isCorrected;
    }
}
