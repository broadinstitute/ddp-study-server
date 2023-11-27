package org.broadinstitute.ddp.client;

import java.time.LocalDateTime;
import java.util.List;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.transformers.LocalDateTimeAdapter;

/**
 * Holds response from Google Recaptcha Verify Service
 *
 *  @see <a href="https://developers.google.com/recaptcha/docs/verify#api_response>Google Recaptcha API docs</a>
 *
 */
public class GoogleRecaptchaVerifyResponse {
    /**
     * whether this request was a valid reCAPTCHA token for your site
     */
    private boolean success;
    /**
     *Applicable for Recaptcha v3 only. the score for this request (0.0 - 1.0)
     */
    private Double score;
    /**
     * timestamp of the challenge load
     */
    @JsonAdapter(LocalDateTimeAdapter.class)
    @SerializedName("challenge_ts")
    private LocalDateTime challengeTimeStamp;
    /**
     * the hostname of the site where the reCAPTCHA was solved
     */
    private String hostname;
    /**
     * Applicable for Recaptcha v3 only. the action name for this request
     */
    private String action;
    /**
     * error codes reported by Google
     */
    @SerializedName("error-codes")
    private List<String> errorCodes;

    public boolean isSuccess() {
        return success;
    }

    public LocalDateTime getChallengeTimeStamp() {
        return challengeTimeStamp;
    }

    public String getHostname() {
        return hostname;
    }

    public List<String> getErrorCodes() {
        return errorCodes;
    }

    public Double getScore() {
        return score;
    }

    public String getAction() {
        return action;
    }

    public boolean isV3() {
        return score != null;
    }

    public boolean isV2() {
        return !isV3();
    }
}
