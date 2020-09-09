package org.broadinstitute.ddp.model.dsm;

import java.time.Instant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.transformers.InstantToIsoDateTimeUtcStrAdapter;

public class TestResult {

    @NotBlank
    @SerializedName("result")
    private String result;

    @NotBlank
    @SerializedName("reason")
    private String reason;

    @NotNull
    @SerializedName("timeCompleted")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant timeCompleted;

    public TestResult(String result, String reason, Instant timeCompleted) {
        this.result = result;
        this.reason = reason;
        this.timeCompleted = timeCompleted;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimeCompleted() {
        return timeCompleted;
    }
}
