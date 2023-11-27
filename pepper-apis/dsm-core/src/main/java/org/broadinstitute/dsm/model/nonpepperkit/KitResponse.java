package org.broadinstitute.dsm.model.nonpepperkit;

import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;

@Getter
public class KitResponse {
    private ErrorMessage errorMessage;
    private String juniperKitId;
    private Object value;
    private List<NonPepperKitStatusDto> kits;
    private boolean isError;

    public static KitResponse makeKitResponseError(ErrorMessage errorMessage, String juniperKitId, Object value) {
        KitResponse kitResponse = new KitResponse();
        kitResponse.errorMessage = errorMessage;
        kitResponse.juniperKitId = juniperKitId;
        kitResponse.value = value;
        kitResponse.isError = true;
        kitResponse.setKitsListNull();
        return kitResponse;
    }

    public static KitResponse makeKitResponseError(ErrorMessage errorMessage) {
        return makeKitResponseError(errorMessage, null, null);
    }

    public static KitResponse makeKitResponseError(ErrorMessage errorMessage, String juniperKitId) {
        return makeKitResponseError(errorMessage, juniperKitId, null);
    }

    public static KitResponse makeKitStatusResponse(List<NonPepperKitStatusDto> kits) {
        KitResponse kitResponse = new KitResponse();
        kitResponse.kits = kits;
        kitResponse.removeErrorFields();
        return kitResponse;
    }

    private void removeErrorFields() {
        this.errorMessage = null;
        this.juniperKitId = null;
        this.value = null;
        this.isError = false;
    }

    private void setKitsListNull() {
        this.kits = null;
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
