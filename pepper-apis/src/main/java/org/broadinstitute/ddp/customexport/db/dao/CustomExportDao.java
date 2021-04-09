package org.broadinstitute.ddp.customexport.db.dao;

import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.customexport.db.dto.CustomExportDto;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface CustomExportDao extends SqlObject {

    default long getLastCompleted(long studyId) {
        Optional<Long> optionalLastCompleted = findExportLastCompletedByStudyId(studyId);
        if (optionalLastCompleted.isPresent()) {
            return optionalLastCompleted.get();
        } else {
            // Return 0 but make sure to add this to the database
            long exportId = insertExport(studyId, 0);
            findExportDtoById(exportId).orElseThrow(() ->
                    new DaoException("Could not find newly created custom export with id " + exportId));
            return (long)0;
        }
    }

    default void updateLastCompleted(long lastCompleted, long studyId) {
        DBUtils.checkUpdate(1, updateLastCompletedByStudyId(lastCompleted, studyId));
    }

    @SqlUpdate("insert into custom_export (umbrella_study_id, custom_export_last_completed) values (:studyId, :lastCompleted)")
    @GetGeneratedKeys
    long insertExport(
            @Bind("studyId") long studyId,
            @Bind("lastCompleted") long lastCompleted
    );

    @SqlQuery("select * from custom_export where custom_export_id = :id")
    @RegisterConstructorMapper(CustomExportDto.class)
    Optional<CustomExportDto> findExportDtoById(@Bind("id") long id);

    @SqlQuery("select custom_export_last_completed from custom_export where umbrella_study_id = :id")
    Optional<Long> findExportLastCompletedByStudyId(@Bind("id") long id);

    @SqlUpdate("update custom_export set custom_export_last_completed = :lastCompleted where umbrella_study_id = :studyId")
    int updateLastCompletedByStudyId(@Bind("lastCompleted") long lastCompleted, @Bind("studyId") long studyId);

    @SqlQuery("SELECT CASE WHEN ( "
            + "SELECT COUNT(user_id) FROM ( "
            + "SELECT user_id FROM user_study_enrollment AS usen  "
            + "JOIN study_activity AS sa ON sa.study_id = usen.study_id  "
            + "JOIN activity_instance AS aci ON usen.user_id = aci.participant_id  "
            + "JOIN activity_instance_status AS ais ON aci.activity_instance_id = ais.activity_instance_id  "
            + "JOIN activity_instance_status_type AS aist ON aist.activity_instance_status_type_id = ais"
            + ".activity_instance_status_type_id  "
            + "WHERE usen.study_id = :studyId AND aist.activity_instance_status_type_code=:statusType "
            + "AND aci.first_completed_at>:lastCompletion AND usen.valid_to IS NULL AND sa.study_activity_code=:activityCode) as ids "
            + ") > "
            + "0 THEN true ELSE false END")
    boolean needCustomExport(@Bind("studyId") long studyId,
                             @Bind("statusType") String statusType,
                             @Bind("lastCompletion") long lastCompletion,
                             @Bind("activityCode") String activityCode);

    @SqlQuery("SELECT usen.user_id FROM user_study_enrollment AS usen "
            + "JOIN study_activity AS sa ON sa.study_id = usen.study_id "
            + "JOIN activity_instance AS aci ON usen.user_id = aci.participant_id "
            + "JOIN activity_instance_status AS ais ON aci.activity_instance_id = ais.activity_instance_id "
            + "JOIN activity_instance_status_type AS aist ON aist.activity_instance_status_type_id = ais.activity_instance_status_type_id "
            + "WHERE usen.study_id = :studyId AND aist.activity_instance_status_type_code=:statusType "
            + "AND aci.first_completed_at>:lastCompletion AND usen.valid_to IS NULL AND sa.study_activity_code=:activityCode "
            + "GROUP BY usen.user_id ORDER BY MAX(aci.first_completed_at) DESC LIMIT :limit OFFSET :offset")
    Set<Long> findCustomUserIdsToExport(@Bind("studyId") long studyId,
                                        @Bind("statusType") String statusType,
                                        @Bind("lastCompletion") long lastCompletion,
                                        @Bind("activityCode") String activityCode,
                                        @Bind("limit") long limit,
                                        @Bind("offset") long offset);

    @SqlQuery("SELECT MAX(aci.first_completed_at) FROM activity_instance AS aci "
            + "JOIN user_study_enrollment AS usen ON aci.participant_id = usen.user_id "
            + "JOIN study_activity AS sa ON aci.study_activity_id = sa.study_activity_id "
            + "JOIN activity_instance_status AS ais ON aci.activity_instance_id = ais.activity_instance_id "
            + "JOIN activity_instance_status_type AS aist ON aist.activity_instance_status_type_id = ais.activity_instance_status_type_id "
            + "WHERE  sa.study_id = :studyId AND aist.activity_instance_status_type_code = :statusType  "
            + "AND sa.study_activity_code=:activityCode AND usen.valid_to IS NULL")
    Optional<Long> getLastCustomCompletionDate(@Bind("studyId") long studyId,
                                               @Bind("statusType") String statusType,
                                               @Bind("activityCode") String activityCode);
}
