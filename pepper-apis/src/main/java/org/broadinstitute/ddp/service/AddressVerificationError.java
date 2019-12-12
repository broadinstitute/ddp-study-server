package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Creating our class to contain Validation errors. We try to keep the structure of the EasyPost validation error
 */
public class AddressVerificationError {
    private String code;
    private String message;
    private List<AddressFieldError> errors = new ArrayList<>();

    private AddressVerificationError() {
        super();
    }

    /**
     * Instantiates an AddressVerificationError object.
     */
    AddressVerificationError(String message, String errorCode) {
        this();
        this.message = message;
        this.code = errorCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<AddressFieldError> getErrors() {
        return errors;
    }

    public class AddressFieldError {
        String code;
        String field;
        String message;
        String suggestion;

        public String getCode() {
            return code;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }


}
