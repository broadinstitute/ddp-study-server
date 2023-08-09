package org.broadinstitute.dsm.model.nonpepperkit;

public class KitResponse {
    public enum UsualErrorMessage {
        ADDRESS_VALIDATION_ERROR("UNABLE_TO_VERIFY_ADDRESS"),
        UNKNOWN_KIT_TYPE("UNKNOWN_KIT_TYPE"),
        UNKNOWN_STUDY("UNKNOWN_STUDY"),
        MISSING_JUNIPER_KIT_ID("MISSING_JUNIPER_KIT_ID"),
        MISSING_JUNIPER_PARTICIPANT_ID("MISSING_JUNIPER_PARTICIPANT_ID"),
        MISSING_STUDY_GUID("MISSING_STUDY_GUID"),
        DSM_ERROR_SOMETHING_WENT_WRONG("DSM_ERROR_SOMETHING_WENT_WRONG"),
        NOT_IMPLEMENTED("NOT_IMPLEMENTED");
        private final String message;

        UsualErrorMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
