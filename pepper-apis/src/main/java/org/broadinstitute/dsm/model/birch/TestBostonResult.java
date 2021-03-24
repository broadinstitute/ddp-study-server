package org.broadinstitute.dsm.model.birch;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.broadinstitute.dsm.jobs.PubSubLookUp;
import org.junit.Test;


@Data
public class TestBostonResult {
    private String orderMessageId;
    private String institutionId;

    @SerializedName("sample_id")
    private String sampleId;
    private String result;
    private String reason;
    @SerializedName ("time_completed")
    private String timeCompleted;
    @SerializedName ("is_corrected")
    private boolean isCorrected;
    private String kitRequestId;

    public TestBostonResult(String result, String timeCompleted, boolean isCorrected, String sampleId){
        this.result = result;
        this.timeCompleted = timeCompleted;
        this.isCorrected = isCorrected;
        this.sampleId = sampleId;

    }

}
