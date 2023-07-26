package org.broadinstitute.dsm.model.nonpepperkit;

public class KitResponseError extends KitResponse {
    public String errorMessage;
    public String juniperKitId;
    public Object value;

    public KitResponseError(String errorMessage, String juniperKitId, Object value) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
        this.value = value;
    }

    public KitResponseError(String errorMessage) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = null;
        this.value = null;
    }

    public KitResponseError(String errorMessage, String juniperKitId) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
    }
}
