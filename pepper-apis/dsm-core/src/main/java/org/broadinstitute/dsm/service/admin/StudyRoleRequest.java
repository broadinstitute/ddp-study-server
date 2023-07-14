package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;


@Data
public class StudyRoleRequest {
    private final List<RoleInfo> roles;

    public StudyRoleRequest(List<RoleInfo> roles) {
        this.roles = roles;
    }

    public static class RoleInfo {
        public final String roleName;
        public final String adminRoleName;

        public RoleInfo(String roleName, String adminRoleName) {
            this.roleName = roleName;
            this.adminRoleName = adminRoleName;
        }
    }
}
