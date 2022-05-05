package org.broadinstitute.dsm.db.jdbi;

import lombok.Data;

@Data
public class RoleDto {
    long roleId;
    String name;
    String description;
    long umbrellaId;

    public RoleDto(long roleId, String name, String description, long umbrellaId) {
        this.roleId = roleId;
        this.name = name;
        this.description = description;
        this.umbrellaId = umbrellaId;
    }
}
