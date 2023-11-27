package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;

/**
 * Created by ebaker on 5/1/17.
 */
@Data
public class Contact {

    private String firstName;
    private String lastName;
    private String email;
    private Long dateCreated;

    public Contact() {
    }

    public Contact(@NonNull String firstName, @NonNull String lastName, @NonNull String email, Long dateCreated) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateCreated = dateCreated;
    }
}
