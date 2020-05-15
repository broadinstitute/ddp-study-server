package org.broadinstitute.ddp.json;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * A response returned when the "/healthcheck" route is called.
 */
public class HealthCheckResponse {
    public static final Integer HC_OK = 0;
    public static final Integer HC_QUERY_TIMED_OUT = 1;
    public static final Integer HC_UNKNOWN_ERROR = 2;

    private static final Map<Integer, String> resultExplanationMap = new HashMap<>();

    static {
        resultExplanationMap.put(HC_OK, "Health check succeeded");
        resultExplanationMap.put(HC_QUERY_TIMED_OUT, "Request timed out");
        resultExplanationMap.put(HC_UNKNOWN_ERROR, "An unknown error occurred");
    }

    @SerializedName("result")
    private Integer result;
    @SerializedName("resultExplanation")
    private String resultExplanation;

    public HealthCheckResponse(Integer result) {
        this.result = result;
        this.resultExplanation = resultExplanationMap.get(result);
    }
}
