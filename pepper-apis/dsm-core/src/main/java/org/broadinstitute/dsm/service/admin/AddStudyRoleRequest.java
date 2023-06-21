package org.broadinstitute.dsm.service.admin;

import lombok.Data;

@Data
public class AddStudyRoleRequest {
    private final String studyGroup;
    private final String role;

    public AddStudyRoleRequest(String studyGroup, String role) {
        this.studyGroup = studyGroup;
        this.role = role;
    }
}
