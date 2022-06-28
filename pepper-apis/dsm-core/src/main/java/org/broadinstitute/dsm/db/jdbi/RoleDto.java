package org.broadinstitute.dsm.db.jdbi;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoleDto {
    long roleId;
    String name;
    String description;
    long umbrellaId;

}
