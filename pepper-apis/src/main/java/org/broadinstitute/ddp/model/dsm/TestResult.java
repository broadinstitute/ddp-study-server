package org.broadinstitute.ddp.model.dsm;

import java.time.Instant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.transformers.InstantToIsoDateTimeUtcStrAdapter;

public class TestResult {

    public static final String NEGATIVE_CODE = "NEGATIVE";
    public static final String POSITIVE_CODE = "POSITIVE";

    @NotBlank
    @SerializedName("result")
    private final String result;

    @NotNull
    @SerializedName("timeCompleted")
    @JsonAdapter(InstantToIsoDateTimeUtcStrAdapter.class)
    private final Instant timeCompleted;

    @SerializedName("isCorrected")
    private final boolean isCorrected;

    public TestResult(String result, Instant timeCompleted, boolean isCorrected) {
        this.result = result;
        this.timeCompleted = timeCompleted;
        this.isCorrected = isCorrected;
    }

    public String getResult() {
        return result;
    }

    public String getNormalizedResult() {
        String res = result.toUpperCase();
        if ("NEG".equals(res)) {
            return NEGATIVE_CODE;
        } else if ("POS".equals(res)) {
            return POSITIVE_CODE;
        } else {
            return res;
        }
    }

    public Instant getTimeCompleted() {
        return timeCompleted;
    }

    public boolean isCorrected() {
        return isCorrected;
    }
}
