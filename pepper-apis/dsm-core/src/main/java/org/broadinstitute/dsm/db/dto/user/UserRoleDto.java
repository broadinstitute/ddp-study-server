package org.broadinstitute.dsm.db.dto.user;

import com.google.gson.annotations.SerializedName;

public class UserRoleDto {
    UserDto user;
    @SerializedName ("name")
    String roleName;
    long roleId;
    long umbrellaId;
    String description;

    public UserRoleDto(long userId, String name, String email, String phoneNumber, String auth0UserId, String guid, String roleName,
                       long roleId, String firstName, String lastName, String description, long umbrellaId) {
        user = new UserDto(userId, name, email, phoneNumber, auth0UserId, guid, firstName, lastName);
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
        this.umbrellaId = umbrellaId;
    }

}
