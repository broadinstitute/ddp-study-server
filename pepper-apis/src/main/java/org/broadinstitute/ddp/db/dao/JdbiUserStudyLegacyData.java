package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserStudyLegacyData extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into user_study_legacy_data(user_id, umbrella_study_id, activity_instance_id, field_name, field_value) "
            + " values(:userId, :studyId, :instanceId, :fieldName, :fieldValue)"
    )
    long insert(
            @Bind("userId") long userId,
            @Bind("studyId") long studyId,
            @Bind("instanceId") Long instanceId,
            @Bind("fieldName") String fieldName,
            @Bind("fieldValue") String fieldValue
    );

}
