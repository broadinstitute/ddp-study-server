package org.broadinstitute.dsm.db.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoleDto {

    String name;
    long roleId;
    String description;
    long umbrellaId;

}
