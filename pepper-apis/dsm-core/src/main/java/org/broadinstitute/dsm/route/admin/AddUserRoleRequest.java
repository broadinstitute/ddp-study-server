package org.broadinstitute.dsm.route.admin;

public class AddUserRoleRequest {

    private final String email;
    private final String group;
    private final String role;

    public AddUserRoleRequest(String email, String group, String role) {
        this.email = email;
        this.group = group;
        this.role = role;
    }

}
