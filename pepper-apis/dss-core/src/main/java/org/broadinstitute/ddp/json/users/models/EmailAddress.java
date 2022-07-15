package org.broadinstitute.ddp.json.users.models;

import javax.validation.constraints.Email;

import lombok.Data;
import lombok.NonNull;

import org.apache.commons.validator.routines.EmailValidator;

@Data
public class EmailAddress {

    @Email
    private final String value;

    /**
     * Creates a new object representing an email address
     * 
     * @param email the represented email
     * @throws IllegalArgumentException if the email is null, empty, or otherwise invalid 
     */
    public EmailAddress(@NonNull String email) throws IllegalArgumentException {
        if (EmailValidator.getInstance().isValid(email) == false) {
            throw new IllegalArgumentException("email format is invalid");
        }

        this.value = email;
    }
}
