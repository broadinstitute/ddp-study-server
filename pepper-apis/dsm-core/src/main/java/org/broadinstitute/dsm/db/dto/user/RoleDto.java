package org.broadinstitute.dsm.db.dto.user;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class RoleDto {
    @SerializedName("name")
    String roleName;
    long roleId;
    long umbrellaId;
    String description;

    public RoleDto(String roleName, long roleId, String description, long umbrellaId) {
        this.roleName = roleName;
        this.roleId = roleId;
        this.description = description;
        this.umbrellaId = umbrellaId;
    }
}
