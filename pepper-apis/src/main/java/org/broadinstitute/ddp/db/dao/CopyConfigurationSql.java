package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.model.event.CopyLocationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface CopyConfigurationSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into copy_configuration (study_id) values (:studyId)")
    long insertCopyConfig(@Bind("studyId") long studyId);

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

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertCompositeCopyConfigPair")
    long insertCompositeCopyConfigPair(
            @Bind("pairId") long pairId,
            @Bind("sourceChildStableCodeId") long sourceChildStableCodeId,
            @Bind("targetChildStableCodeId") long targetChildStableCodeId);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlBatch("insertCompositeCopyConfigPair")
    long[] bulkInsertCompositeCopyConfigPair(
            @Bind("pairId") long pairId,
            @Bind("sourceChildStableCodeId") List<Long> sourceChildStableCodeIds,
            @Bind("targetChildStableCodeId") List<Long> targetChildStableCodeIds);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertCompositeCopyConfigPairByQuestionStableIds")
    long insertCompositeCopyConfigPairByQuestionStableIds(
            @Bind("pairId") long pairId,
            @Bind("studyId") long studyId,
            @Bind("sourceChildStableId") String sourceChildStableId,
            @Bind("targetChildStableId") String targetChildStableId);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlBatch("insertCompositeCopyConfigPairByQuestionStableIds")
    long[] bulkInsertCompositeCopyConfigPairByQuestionStableIds(
            @Bind("pairId") long pairId,
            @Bind("studyId") long studyId,
            @Bind("sourceChildStableId") List<String> sourceChildStableIds,
            @Bind("targetChildStableId") List<String> targetChildStableIds);

    @SqlUpdate("delete from copy_configuration where copy_configuration_id = :id")
    int deleteCopyConfig(@Bind("id") long configId);

    @SqlUpdate("delete from copy_location where copy_location_id = :locId")
    int deleteCopyLocation(@Bind("locId") long locationId);

    @SqlUpdate("delete from copy_location where copy_location_id in (<locIds>)")
    int bulkDeleteCopyLocations(@BindList(value = "locIds", onEmpty = EmptyHandling.NULL) Set<Long> locationIds);
}
