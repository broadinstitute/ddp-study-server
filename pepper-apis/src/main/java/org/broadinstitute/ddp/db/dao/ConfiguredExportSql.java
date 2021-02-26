package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ConfiguredExportSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into configured_export "
            + "(study_id, is_enabled, run_schedule, bucket_type, bucket_name, file_path) values "
            + "(:studyId, :isEnabled, :runSchedule, :bucketType, :bucketName, :filePath)")
    long insertConfiguredExport(@Bind("studyId") long studyId,
                                @Bind("isEnabled") boolean isEnabled,
                                @Bind("runSchedule") String runSchedule,
                                @Bind("bucketType") String bucketType,
                                @Bind("bucketName") String bucketName,
                                @Bind("filePath") String filePath);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_participant_field (configured_export_id, excluded_participant_field)"
            + " select configured_export_id, :excludedParticipantField "
            + " where study_id = :studyId")
    long insertExcludedParticipantFieldByStudyId(
                                @Bind("excludedParticipantField") String excludedParticipantField,
                                @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_activity (configured_export_id, study_activity_id, is_incremental) "
            + " select configured_export_id ")
    long insertExportActivityByStudyIdAndActivityCode(
                                @Bind("studyActivityId") long studyActivityId,
                                @Bind("isIncremental") boolean isIncremental,
                                @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_activity_field (export_activity_id, excluded_activity_field) "
            + " select export_activity_id, :excludedActivityField from export_activity "
            +  " where study_id = :studyId")
    long insertExcludedActivityFieldByActivityCode(
                                @Bind("excludedActivityField") String excludedActivityField,
                                @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_metadata_field (export_activity_id, excluded_metadata_field) "
            + " select export_activity_id, :excludedMetadataField from export_activity "
            +  " where study_id = :studyId")
    long insertExcludedMetadataFieldByStudyId(
                                @Bind("excludedMetadataField") String excludedMetadataField,
                                @Bind("studyId") long studyId);

    //TODO
}
