package org.broadinstitute.ddp.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of error object from doing EasyPost strict address verification. This is needed since EasyPost library
 * does not provide a nice error representation.
 */
public class EasyPostVerifyError {

    private String code;
    private String message;
    private List<FieldError> errors = new ArrayList<>();

    public EasyPostVerifyError(String code, String message) {
        this.message = message;
        this.code = code;
    }

    public EasyPostVerifyError(String code, String message, List<FieldError> errors) {
        this.code = code;
        this.message = message;
        this.errors.addAll(errors);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public static class FieldError {

        private String code;
        private String field;
        private String message;
        private String suggestion;

        public FieldError(String code, String field, String message, String suggestion) {
            this.code = code;
            this.field = field;
            this.message = message;
            this.suggestion = suggestion;
        }

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
