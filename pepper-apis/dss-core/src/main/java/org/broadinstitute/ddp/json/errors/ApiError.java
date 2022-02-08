package org.broadinstitute.ddp.json.errors;

import com.google.gson.annotations.SerializedName;

/**
 * Root of API error response object hierarchy.
 */
public class ApiError {

    @SerializedName("code")
    private String code;
    @SerializedName("message")
    private String message;

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
