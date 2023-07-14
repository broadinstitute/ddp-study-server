package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

public class UserInfo {
    public final String email;
    public final String name;
    public final String phone;
    public List<UserRole> roles;

    public UserInfo(String email, String name, String phone, List<UserRole> roles) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.roles = roles;
    }

    public UserInfo(String email, String name, String phone) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.roles = new ArrayList<>();
    }

    public void addRoles(List<UserRole> roles) {
        this.roles = roles;
    }
}
