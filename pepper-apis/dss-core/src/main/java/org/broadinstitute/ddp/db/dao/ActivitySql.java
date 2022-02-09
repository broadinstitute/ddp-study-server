package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.study.ActivityMappingType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ActivitySql extends SqlObject {

    @SqlUpdate("insert into study_activity_mapping"
            + "        (umbrella_study_id, activity_mapping_type_id, study_activity_id, sub_activity_stable_id)"
            + " values ((select umbrella_study_id from umbrella_study where guid = :studyGuid),"
            + "         (select activity_mapping_type_id from activity_mapping_type where activity_mapping_code = :type),"
            + "         :activityId, :subActivityStableId)")
    int insertActivityMapping(
            @Bind("studyGuid") String studyGuid,
            @Bind("type") ActivityMappingType type,
            @Bind("activityId") long activityId,
            @Bind("subActivityStableId") String subActivityStableId);
}
