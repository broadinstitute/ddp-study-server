package org.broadinstitute.dsm.careevolve;

import com.google.gson.annotations.SerializedName;

public class OrderResponse {

    @SerializedName("hl7AcknowledgementMessage")
    private  String hl7Ack;

    @SerializedName("handle")
    private String handle;

    @SerializedName("errorDetail")
    private String error;

    public String getHl7Ack() {
        return hl7Ack;
    }

    public String getHandle() {
        return handle;
    }

    public String getError() {
        return error;
    }
}
