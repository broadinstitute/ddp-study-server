package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ConfiguredExportSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into configured_export"
            + " (study_id, is_enabled, run_schedule, bucket_type, bucket_name, file_path) values"
            + " (:studyId, :isEnabled, :runSchedule, :bucketType, :bucketName, :filePath)")
    long insertConfiguredExport(@Bind("studyId") long studyId,
                                @Bind("isEnabled") boolean isEnabled,
                                @Bind("runSchedule") String runSchedule,
                                @Bind("bucketType") String bucketType,
                                @Bind("bucketName") String bucketName,
                                @Bind("filePath") String filePath);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_participant_field (configured_export_id, excluded_participant_field) values"
            + " ((select configured_export_id from configured_export"
            + " where study_id = :studyId), :excludedParticipantField)")
    long insertExcludedParticipantFieldByStudyId(
                                @Bind("studyId") long studyId, @Bind("excludedParticipantField") String excludedParticipantField);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_activity (configured_export_id, activity_id, is_incremental) values"
            + " (:configuredExportId, (select study_activity_id from study_activity "
            + "where study_id=:studyId and study_activity_code=:activityCode), :isIncremental)")
    long insertExportActivity(
            @Bind("configuredExportId") long configuredExportId,
            @Bind("activityCode") String activityCode,
            @Bind("studyId") long studyId,
            @Bind("isIncremental") boolean isIncremental);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_activity_field (export_activity_id, excluded_activity_field) values"
            + " (:exportActivityId, :excludedActivityField)")
    long insertExcludedActivityFieldByActivityId(
                                @Bind("exportActivityId") long exportActivityId,
                                @Bind("excludedActivityField") String excludedActivityField);

    @GetGeneratedKeys
    @SqlUpdate("insert into excluded_metadata_field (export_activity_id, excluded_metadata_field) values"
            + " (:exportActivityId, :excludedMetadataField)")
    long insertExcludedMetadataFieldByActivityId(
                                @Bind("exportActivityId") long exportActivityId,
                                @Bind("excludedMetadataField") String excludedMetadataField);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_filter (export_activity_id, export_filter_type_id) values"
            + " (:exportActivityId, (select export_filter_type_id from export_filter_type"
            + " where export_filter_type = :filterType))")
    long insertExportFilterByActivityId(
                                @Bind("exportActivityId") long exportActivityId,
                                @Bind("filterType") String filterType);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_activity_status_filter (export_filter_id, export_filter_status_type_id) values"
            + " (:exportFilterId, (select activity_instance_status_type_id from activity_instance_status_type"
            + " where activity_instance_status_type_code = :filterStatus))")
    long insertExportActivityStatusFilterByExportFilterId(
                                @Bind("exportFilterId") long exportFilterId,
                                @Bind("filterStatus") String filterStatus);

    @GetGeneratedKeys
    @SqlUpdate("insert into export_first_field (export_activity_id, export_first_field) values"
            + "(:exportActivityId, :exportFirstField)")
    long insertExportFirstFieldByActivityId(
                                @Bind("exportActivityId") long exportActivityId,
                                @Bind("exportFirstField") String exportFirstField);
}
