package org.broadinstitute.dsm.db.jdbi;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiRole extends SqlObject {

    @SqlQuery ("SELECT r.role_id, r.name, r.description, r.umbrella_id FROM  role r  " +
            "left join  umbrella_study us on (r.umbrella_id = us.umbrella_id)  " +
            "where us.guid =:umbrellaGuid")
    @RegisterConstructorMapper (RoleDto.class)
    List<RoleDto> getAllRolesForStudy(@Bind ("umbrellaGuid") String studyGuid);


}
