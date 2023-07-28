package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class UserInfo {
    private final String email;
    private final String name;
    private final String phone;
    private List<UserRole> roles;

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
}
