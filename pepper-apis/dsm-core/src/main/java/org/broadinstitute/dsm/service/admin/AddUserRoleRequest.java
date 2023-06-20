package org.broadinstitute.dsm.service.admin;

import lombok.Data;

@Data
public class AddUserRoleRequest {

    private final String email;
    private final String studyGroup;
    private final String role;

    public AddUserRoleRequest(String email, String studyGroup, String role) {
        this.email = email;
        this.studyGroup = studyGroup;
        this.role = role;
    }

}
