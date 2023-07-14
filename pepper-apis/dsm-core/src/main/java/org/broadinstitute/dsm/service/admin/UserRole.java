package org.broadinstitute.dsm.service.admin;

public class UserRole {
    public final String name;
    public final String displayText;
    public final boolean hasRole;

    public UserRole(String name, String displayText, boolean hasRole) {
        this.name = name;
        this.displayText = displayText;
        this.hasRole = hasRole;
    }
}
