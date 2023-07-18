package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class StudyRoleResponse {
    private List<Role> roles;

    public StudyRoleResponse(List<Role> roles) {
        this.roles = roles;
    }

    public StudyRoleResponse() {
        this.roles = new ArrayList<>();
    }

    public void addRole(StudyRoleResponse.Role role) {
        this.roles.add(role);
    }

    @Data
    public static class Role {
        private final String name;
        private final String displayText;

        public Role(String name, String displayText) {
            this.name = name;
            this.displayText = displayText;
        }
    }
}
