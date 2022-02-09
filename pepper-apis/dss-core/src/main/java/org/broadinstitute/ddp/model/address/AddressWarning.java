package org.broadinstitute.ddp.model.address;

import com.google.gson.annotations.SerializedName;

public class AddressWarning {

    @SerializedName("code")
    private String code;
    @SerializedName("message")
    private String message;

    public AddressWarning(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public AddressWarning(Warn warn) {
        this(warn.getCode(), warn.getMessage());
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    // A mapping of currently available warnings.
    public enum Warn {
        ZIP_UNSUPPORTED("W.ZIP.UNSUPPORTED", "Zip code is not supported");

        private String code;
        private String message;

        Warn(String code, String message) {
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
}
