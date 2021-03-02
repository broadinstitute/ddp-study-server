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
            + " select configured_export_id, :excludedParticipantField from configured_export"
            + " where study_id = :studyId")
    long insertExcludedParticipantFieldByStudyId(
                                @Bind("excludedParticipantField") String excludedParticipantField,
                                @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_activity (study_activity_id, is_incremental) "
            + " select study_activity_id, :isIncremental from study_activity"
            + " where study_activity_code = :activityCode")
    long insertExportActivityByStudyActivityIdAndCode(
                                @Bind("studyActivityId") long studyActivityId,
                                @Bind("isIncremental") boolean isIncremental,
                                @Bind("activityCode") String activityCode);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_activity_field (export_activity_id, excluded_activity_field) "
            + " :exportActivityId, :excludedActivityField")
    long insertExcludedActivityFieldByActivityId(
                                @Bind("excludedActivityField") String excludedActivityField,
                                @Bind("exportActivityId") long exportActivityId);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_metadata_field (export_activity_id, excluded_metadata_field) "
            + " :exportActivityId, :excludedMetadataField")
    long insertExcludedMetadataFieldByActivityId(
                                @Bind("excludedMetadataField") String excludedMetadataField,
                                @Bind("exportActivityId") long exportActivityId);

    //TODO: ExportActivityStatusFilter

    //TODO: ExportFilter

    //TODO: ExportFirstField
}
