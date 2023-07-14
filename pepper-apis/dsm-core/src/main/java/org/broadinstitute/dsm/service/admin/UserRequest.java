package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class UserRequest {
    private final List<String> users;

    public UserRequest(List<String> users) {
        this.users = users;
    }
}
