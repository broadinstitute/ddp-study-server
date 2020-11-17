package org.broadinstitute.ddp.db.dao;

import java.util.Set;

import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface CopyConfigurationSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into copy_configuration (study_id, copy_from_previous_instance) values (:studyId, :copyFromPreviousInstance)")
    long insertCopyConfig(@Bind("studyId") long studyId, @Bind("copyFromPreviousInstance") boolean copyFromPreviousInstance);

    @GetGeneratedKeys
    @SqlUpdate("insert into copy_location (copy_location_type_id)"
            + " select copy_location_type_id from copy_location_type where copy_location_type_code = :type")
    long insertCopyLocation(@Bind("type") CopyLocationType type);

    @SqlUpdate("insert into copy_answer_location (copy_location_id, question_stable_code_id)"
            + " values (:locId, :questionStableCodeId)")
    int insertCopyAnswerLocation(
            @Bind("locId") long locationId,
            @Bind("questionStableCodeId") long questionStableCodeId);

    @SqlUpdate("insert into copy_answer_location (copy_location_id, question_stable_code_id)"
            + " select :locId, question_stable_code_id from question_stable_code"
            + "  where umbrella_study_id = :studyId and stable_id = :questionStableId")
    int insertCopyAnswerLocationByQuestionStableId(
            @Bind("locId") long locationId,
            @Bind("studyId") long studyId,
            @Bind("questionStableId") String questionStableId);

    @GetGeneratedKeys
    @SqlUpdate("insert into copy_configuration_pair (copy_configuration_id, source_location_id, target_location_id, execution_order)"
            + " values (:configId, :sourceLocId, :targetLocId, :order)")
    long insertCopyConfigPair(
            @Bind("configId") long configId,
            @Bind("sourceLocId") long sourceLocId,
            @Bind("targetLocId") long targetLocId,
            @Bind("order") int order);

    @SqlUpdate("delete from copy_configuration where copy_configuration_id = :id")
    int deleteCopyConfig(@Bind("id") long configId);

    @SqlUpdate("delete from copy_location where copy_location_id = :locId")
    int deleteCopyLocation(@Bind("locId") long locationId);

    @SqlUpdate("delete from copy_location where copy_location_id in (<locIds>)")
    int bulkDeleteCopyLocations(@BindList(value = "locIds", onEmpty = EmptyHandling.NULL) Set<Long> locationIds);
}
