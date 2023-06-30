package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

public class UserRoleResponse {

    private List<UserRoles> userRolesList;

    public UserRoleResponse() {
        this.userRolesList = new ArrayList<>();
    }

    public void addUserRoles(String userEmail, List<String> roles) {
        this.userRolesList.add(new UserRoles(userEmail, roles));
    }

    public static class UserRoles {
        public final String user;
        public final List<String> roles;

        public UserRoles(String user, List<String> roles) {
            this.user = user;
            this.roles = roles;
        }
    }
}
