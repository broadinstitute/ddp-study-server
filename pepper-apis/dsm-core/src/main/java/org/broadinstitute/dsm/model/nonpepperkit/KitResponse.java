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

    public KitResponse makeKitResponseError(ErrorMessage errorMessage, String juniperKitId, Object value) {
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
        this.value = value;
        this.isError = true;
        setKitsListNull();
        return this;
    }

    public KitResponse makeKitResponseError(ErrorMessage errorMessage) {
        this.errorMessage = errorMessage;
        this.juniperKitId = null;
        this.value = null;
        this.isError = true;
        return this;
    }

    public KitResponse makeKitResponseError(ErrorMessage errorMessage, String juniperKitId) {
        this.errorMessage = errorMessage;
        this.juniperKitId = juniperKitId;
        this.isError = true;
        return this;
    }

    public KitResponse makeKitStatusResponse(List<NonPepperKitStatusDto> kits) {
        this.kits = kits;
        this.removeErrorFields();
        return this;
    }

    private void removeErrorFields() {
        this.errorMessage = null;
        this.juniperKitId = null;
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
