package org.broadinstitute.dsm.service.admin;

import java.util.Collections;
import java.util.List;

import lombok.Data;

@Data
public class SetUserRoleRequest {

    private final List<String> users;
    private final List<String> roles;

    /**
     * Create a new one for the given list of email addresses
     * and roles
     */
    public SetUserRoleRequest(List<String> users, List<String> roles) {
        this.users = users;
        this.roles = roles;
    }

    /**
     * Create a new one for the given email and role
     */
    public SetUserRoleRequest(String userEmail, String role) {
        this(Collections.singletonList(userEmail), Collections.singletonList(role));
    }

    /**
     * Create a new one for the given email and roles
     */
    public SetUserRoleRequest(String userEmail, List<String> roles) {
        this(Collections.singletonList(userEmail), roles);
    }
}

