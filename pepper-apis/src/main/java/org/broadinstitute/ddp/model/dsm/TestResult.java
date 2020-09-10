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

    @NotNull
    @SerializedName("timeCompleted")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private Instant timeCompleted;

    public TestResult(String result, Instant timeCompleted) {
        this.result = result;
        this.timeCompleted = timeCompleted;
    }

    public String getResult() {
        return result;
    }

    public String getNormalizedResult() {
        String res = result.toUpperCase();
        if ("NEG".equals(res)) {
            return "NEGATIVE";
        } else if ("POS".equals(res)) {
            return "POSITIVE";
        } else {
            return res;
        }
    }

    public Instant getTimeCompleted() {
        return timeCompleted;
    }
}
