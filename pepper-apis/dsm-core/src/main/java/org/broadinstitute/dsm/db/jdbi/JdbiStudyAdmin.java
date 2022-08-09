package org.broadinstitute.dsm.db.jdbi;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiStudyAdmin extends SqlObject {
    @SqlUpdate("INSERT INTO study_admin (umbrella_study_id, user_id) SELECT umbrella_study_id, :userId "
            + "from umbrella_Study where guid = :umbrellaGuid")
    int insert(@Bind("umbrellaGuid") String umbrellaGuid, @Bind("userId") long userId);
}
