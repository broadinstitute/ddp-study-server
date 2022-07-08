package org.broadinstitute.ddp.json.users.models;

import javax.validation.constraints.Email;

import lombok.Data;
import org.apache.commons.validator.routines.EmailValidator;

@Data
public class EmailAddress {

    @Email
    private final String value;

    public EmailAddress(String email) {
        if (EmailValidator.getInstance().isValid(email) == false) {
            throw new IllegalArgumentException("email format is invalid");
        }

        this.value = email;
    }
}
