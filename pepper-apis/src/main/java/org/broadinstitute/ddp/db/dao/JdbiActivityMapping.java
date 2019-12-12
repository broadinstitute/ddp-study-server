package org.broadinstitute.ddp.db.dao;

import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;
import org.broadinstitute.ddp.model.dsm.StudyActivityMapping;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityMapping extends SqlObject {
    @SqlUpdate("INSERT INTO "
            + "   study_activity_mapping "
            + "   (umbrella_study_id, "
            + "    activity_mapping_type_id, "
            + "    study_activity_id, "
            + "    sub_activity_stable_id) "
            + "VALUES"
            + "   ((SELECT "
            + "           study.umbrella_study_id "
            + "       FROM "
            + "           umbrella_study as study "
            + "       WHERE "
            + "           study.guid = :studyGUID), "
            + "   (SELECT "
            + "           activity_mapping_type_id "
            + "    FROM "
            + "           activity_mapping_type "
            + "    WHERE "
            + "           activity_mapping_code = :activityMappingCode), "
            + "    :studyActivityId, "
            + "    :subActivityStableId) ")
    int insert(@Bind("studyGUID") String studyGUID,
               @Bind("activityMappingCode") String activityMappingCode,
               @Bind("studyActivityId") long studyActivityId,
               @Bind("subActivityStableId") String subActivityStableId);

    default void insertMapping(String studyGuid, String mappingCode, long activityId, String subStableId) {
        int numInserted = insert(studyGuid, mappingCode, activityId, subStableId);
        if (numInserted != 1) {
            throw new DaoException(String.format(
                    "Could not insert mapping for study guid %s with code=%s, activityId=%d, stableId=%s",
                    studyGuid, mappingCode, activityId, subStableId));
        }
    }

    @SqlQuery("SELECT "
            + "   study.guid as study_guid, "
            + "   activity_mapping_type.activity_mapping_code as activity_mapping_type, "
            + "   activity_mapping.study_activity_id, "
            + "   activity_mapping.sub_activity_stable_id "
            + "FROM "
            + "   study_activity_mapping as activity_mapping "
            + "JOIN umbrella_study as study ON activity_mapping.umbrella_study_id = study.umbrella_study_id "
            + "JOIN activity_mapping_type ON activity_mapping.activity_mapping_type_id = activity_mapping_type.activity_mapping_type_id "
            + "WHERE "
            + "   study.umbrella_study_id = :studyId"
            + "   and activity_mapping_type.activity_mapping_code = :mappingType")
    @RegisterConstructorMapper(StudyActivityMapping.class)
    Stream<StudyActivityMapping> getActivityMappingForStudyAndActivityType(@Bind("studyId") long studyId,
                                                                           @Bind("mappingType") ActivityMappingType mappingType);
}
