package org.broadinstitute.dsm.service.admin;

import lombok.Data;

@Data
public class UserRole {
    private final String name;
    private final String displayText;
    private final boolean hasRole;

    public UserRole(String name, String displayText, boolean hasRole) {
        this.name = name;
        this.displayText = displayText;
        this.hasRole = hasRole;
    }
}
