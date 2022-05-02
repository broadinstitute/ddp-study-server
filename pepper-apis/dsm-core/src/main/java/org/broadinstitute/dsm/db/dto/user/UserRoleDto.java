package org.broadinstitute.dsm.db.dto.user;

public class UserRoleDto {
    UserDto user;
    String roleName;
    long roleId;

    public UserRoleDto(long userId, String name, String email, String phoneNumber, String auth0UserId, String guid, String roleName,
                       long roleId,
                       String firstName, String lastName) {
        user = new UserDto(userId, name, email, phoneNumber, auth0UserId, guid, firstName, lastName);
        this.roleId = roleId;
        this.roleName = roleName;
    }

}
