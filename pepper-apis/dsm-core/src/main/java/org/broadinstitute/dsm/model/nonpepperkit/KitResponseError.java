package org.broadinstitute.dsm.model.nonpepperkit;

import lombok.Getter;

@Getter
public class KitResponseError extends KitResponse {
    private ErrorMessage errorMessage;
    private String juniperKitId;
    private Object value;

    public KitResponseError(ErrorMessage errorMessage, String juniperKitId, Object value) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
        this.value = value;
    }

    public KitResponseError(ErrorMessage errorMessage) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = null;
        this.value = null;
    }

    public KitResponseError(ErrorMessage errorMessage, String juniperKitId) {
        super();
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
    }

    public enum ErrorMessage {
        ADDRESS_VALIDATION_ERROR,
        UNKNOWN_KIT_TYPE,
        UNKNOWN_STUDY,
        MISSING_JUNIPER_KIT_ID,
        MISSING_JUNIPER_PARTICIPANT_ID,
        MISSING_STUDY_GUID,
        DSM_ERROR_SOMETHING_WENT_WRONG,
        EMPTY_REQUEST,
        JSON_SYNTAX_EXCEPTION,
        EMPTY_STUDY_NAME, EMPTY_KIT_TYPE, NOT_IMPLEMENTED

    }
}

