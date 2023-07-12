package org.broadinstitute.dsm.service.admin;

import java.util.Map;

public class StudyRoleResponse {
    private Map<String, String> studyRoles;

    public StudyRoleResponse(Map<String, String> studyRoles) {
        this.studyRoles = studyRoles;
    }
}
