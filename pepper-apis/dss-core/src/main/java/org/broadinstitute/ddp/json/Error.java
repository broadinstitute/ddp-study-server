package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class Error {
    @SerializedName("errorCode")
    private String errorCode;

    public Error(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
