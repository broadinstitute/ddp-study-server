package org.broadinstitute.dsm.service.admin;

import lombok.Data;


@Data
public class StudyRoleRequest {
    private final String studyGroup;
    private final String role;

    public StudyRoleRequest(String studyGroup, String role) {
        this.studyGroup = studyGroup;
        this.role = role;
    }
}
