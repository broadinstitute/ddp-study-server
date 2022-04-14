package org.broadinstitute.ddp.json.auth0;

import java.util.regex.Pattern;

public class Auth0CallResponse {
    private int httpStatus;
    private Auth0Status auth0Status;
    private String errorMessage;

    public Auth0CallResponse(int httpStatus, String errorMessage) {
        this.httpStatus = httpStatus;
        this.auth0Status = auth0Status;
        this.errorMessage = errorMessage;

        auth0Status = Auth0Status.UNKNOWN_PROBLEM;
        if (httpStatus == 200) {
            auth0Status = Auth0Status.SUCCESS;
        } else if (httpStatus == 400) {
            if (errorMessage != null) {
                boolean isEmailMalformed = Pattern.compile(
                        "Payload validation error: 'Object didn't pass validation for format email"
                ).matcher(errorMessage).find();
                boolean isPasswordTooWeak = Pattern.compile(
                        "PasswordStrengthError: Password is too weak"
                ).matcher(errorMessage).find();
                boolean emailAlreadyExists = Pattern.compile(
                        "The specified new email already exists"
                ).matcher(errorMessage).find();
                if ("Invalid token".equals(errorMessage)) {
                    auth0Status = Auth0Status.INVALID_TOKEN;
                } else if (isPasswordTooWeak) {
                    auth0Status = Auth0Status.PASSWORD_TOO_WEAK;
                } else if (isEmailMalformed) {
                    auth0Status = Auth0Status.MALFORMED_EMAIL;
                } else if (emailAlreadyExists) {
                    auth0Status = Auth0Status.EMAIL_ALREADY_EXISTS;
                }
            }
        }
    }

    public Auth0Status getAuth0Status() {
        return auth0Status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static enum Auth0Status {
        SUCCESS, EMAIL_ALREADY_EXISTS, INVALID_TOKEN, PASSWORD_TOO_WEAK, MALFORMED_EMAIL, UNKNOWN_PROBLEM
    }

}
