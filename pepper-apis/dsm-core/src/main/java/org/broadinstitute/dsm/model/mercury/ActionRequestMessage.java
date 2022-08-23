package org.broadinstitute.dsm.model.mercury;

import com.google.gson.annotations.SerializedName;

public class ActionRequestMessage {
    ActionRequest actionRequest;

    public ActionRequestMessage(String orderId) {
        this.actionRequest = new ActionRequest(orderId);
    }

    class ActionRequest {
        @SerializedName("action")
        String action = "checkStatus";

        @SerializedName("orderID")
        String orderId;

        public ActionRequest(String orderId) {
            this.orderId = orderId;
        }
    }
}
