package org.broadinstitute.dsm.db.dto.user;

import lombok.Data;

@Data
public class UserRoleDto {
    UserDto user;
    RoleDto role;

    public UserRoleDto(long userId, String name, String email, String phoneNumber, String auth0UserId, String guid, String roleName,
                       long roleId, String firstName, String lastName, String description, long umbrellaId) {
        this.user = new UserDto(userId, name, email, phoneNumber, auth0UserId, guid, firstName, lastName);
        this.role = new RoleDto(roleName, roleId, description, umbrellaId);
    }

}
