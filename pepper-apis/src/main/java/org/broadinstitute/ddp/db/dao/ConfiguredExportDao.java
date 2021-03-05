package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.export.ConfiguredExport;
import org.broadinstitute.ddp.model.export.ExcludedActivityField;
import org.broadinstitute.ddp.model.export.ExcludedMetadataField;
import org.broadinstitute.ddp.model.export.ExcludedParticipantField;
import org.broadinstitute.ddp.model.export.ExportActivity;
import org.broadinstitute.ddp.model.export.ExportActivityStatusFilter;
import org.broadinstitute.ddp.model.export.ExportFilter;
import org.broadinstitute.ddp.model.export.ExportFirstField;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface ConfiguredExportDao extends SqlObject {

    //Convenience methods

    default ConfiguredExport createConfiguredExport(ConfiguredExport configuredExport) {
        long configuredExportId = insertConfiguredExport(configuredExport.getStudyId(),
                configuredExport.getEnabled(), configuredExport.getRunSchedule(), configuredExport.getBucketType(),
                configuredExport.getBucketName(), configuredExport.getFilePath());

        return findExportById(configuredExportId).orElseThrow(() ->
          new DaoException("Could not find newly created configured export with id " + configuredExportId));
    }

    default ExcludedParticipantField createExcludedParticipantField(ExcludedParticipantField field) {
        long fieldId = insertExcludedParticipantFieldByStudyId(field.getExportId(),
                field.getExcludedParticipantField());

        return findExcludedParticipantFieldById(fieldId).orElseThrow(() ->
                new DaoException("Could not find newly created excluded participant field with id " + fieldId));
    }

    default ExportActivity createExportActivity(ExportActivity activity) {
        long exportActivityId = insertExportActivity(activity.getExportId(), activity.getActivityCode(),
                        activity.getStudyId(), activity.isIncremental());

        return findActivityById(exportActivityId).orElseThrow(() ->
          new DaoException("Could not find newly created export activity with id " + exportActivityId));
    }

    default ExcludedActivityField createExcludedActivityField(ExcludedActivityField field) {
        long fieldId = insertExcludedActivityFieldByActivityId(field.getActivityId(), field.getExcludedActivityField());

        return findExcludedActivityFieldById(fieldId).orElseThrow(() ->
                new DaoException("Could not find newly created excluded activity field with id " + fieldId));
    }

    default ExcludedMetadataField createExcludedMetadataField(ExcludedMetadataField field) {
        long fieldId = insertExcludedMetadataFieldByActivityId(field.getActivityId(), field.getExcludedMetadataField());

        return findExcludedMetadataFieldById(fieldId).orElseThrow(() ->
                new DaoException("Could not find newly created excluded metadata field with id " + fieldId));
    }

    default ExportFirstField createExportFirstField(ExportFirstField field) {
        long exportFirstFieldId = insertExportFirstFieldByActivityId(field.getActivityId(), field.getFirstField());

        return findFirstFieldById(exportFirstFieldId).orElseThrow(() ->
                new DaoException("Could not find newly created excluded metadata field with id " + exportFirstFieldId));
    }

    default ExportFilter createExportFilter(ExportFilter filter) {
        long exportFilterId = insertExportFilterByActivityId(filter.getExportActivityId(), filter.getFilterType());

        return findFilterById(exportFilterId).orElseThrow(() ->
          new DaoException("Could not find newly created export filter with id " + exportFilterId));
    }

    default ExportActivityStatusFilter createExportActivityStatusFilter(ExportActivityStatusFilter filter) {
        long exportActivityStatusFilterId = insertExportActivityStatusFilterByExportFilterId(filter.getFilterId(),
                filter.getStatusType());

        return findActivityStatusFilterById(exportActivityStatusFilterId).orElseThrow(() ->
                new DaoException("Could not find newly created export activity status filter with id "
                        + exportActivityStatusFilterId));
    }

    default void deleteFullConfiguredExportByStudyId(long studyId) {
        long exportId = findExportByStudyId(studyId).orElseThrow(() ->
                new DaoException("Could not find export with study ID " + studyId + " to delete")).getId();

        deleteExportFirstFieldsByExportId(exportId);
        deleteExportActivityStatusFiltersByExportId(exportId);
        deleteExportFiltersByExportId(exportId);
        deleteExcludedMetadataFieldsByExportId(exportId);
        deleteExcludedActivityFieldsByExportId(exportId);
        deleteExportActivitiesByExportId(exportId);
        deleteExcludedParticipantFieldsByExportId(exportId);
        deleteConfiguredExportById(exportId);
    }


    //SQL

    // Insert
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

    // Select
    @SqlQuery("select * from configured_export where configured_export_id = :id")
    @RegisterConstructorMapper(ConfiguredExport.class)
    Optional<ConfiguredExport> findExportById(@Bind("id") long id);

    @SqlQuery("select * from configured_export where study_id = :id")
    @RegisterConstructorMapper(ConfiguredExport.class)
    Optional<ConfiguredExport> findExportByStudyId(@Bind("id") long id);

    @SqlQuery("select * from excluded_participant_field where excluded_participant_field_id = :id")
    @RegisterConstructorMapper(ExcludedParticipantField.class)
    Optional<ExcludedParticipantField> findExcludedParticipantFieldById(@Bind("id") long id);

    @SqlQuery("select * from excluded_participant_field where configured_export_id = :id")
    @RegisterConstructorMapper(ExcludedParticipantField.class)
    Optional<List<ExcludedParticipantField>> findExcludedParticipantFieldsByExportId(@Bind("id") long id);

    @SqlQuery("select * from export_activity where export_activity_id = :id")
    @RegisterConstructorMapper(ExportActivity.class)
    Optional<ExportActivity> findActivityById(@Bind("id") long id);

    @SqlQuery("select * from export_activity where configured_export_id = :exportId")
    @RegisterConstructorMapper(ExportActivity.class)
    Optional<List<ExportActivity>> findActivitiesByExportId(@Bind("exportId") long exportId);

    @SqlQuery("select * from excluded_activity_field where excluded_activity_field_id = :id")
    @RegisterConstructorMapper(ExcludedActivityField.class)
    Optional<ExcludedActivityField> findExcludedActivityFieldById(@Bind("id") long id);

    @SqlQuery("select * from excluded_activity_field where export_activity_id = :id")
    @RegisterConstructorMapper(ExcludedActivityField.class)
    Optional<List<ExcludedActivityField>> findExcludedActivityFieldByActivityIds(@Bind("id") long id);

    @SqlQuery("select * from excluded_metadata_field where excluded_metadata_field_id = :id")
    @RegisterConstructorMapper(ExcludedMetadataField.class)
    Optional<ExcludedMetadataField> findExcludedMetadataFieldById(@Bind("id") long id);

    @SqlQuery("select * from excluded_metadata_field where export_activity_id = :id")
    @RegisterConstructorMapper(ExcludedMetadataField.class)
    Optional<List<ExcludedMetadataField>> findExcludedMetadataFieldsByActivityId(@Bind("id") long id);

    @SqlQuery("select * from export_first_field where export_first_field_id = :id")
    @RegisterConstructorMapper(ExportFirstField.class)
    Optional<ExportFirstField> findFirstFieldById(@Bind("id") long id);

    @SqlQuery("select * from export_first_field where export_activity_id = :id")
    @RegisterConstructorMapper(ExportFirstField.class)
    Optional<List<ExportFirstField>> findFirstFieldsByActivityId(@Bind("id") long id);

    @SqlQuery("select * from export_filter where export_filter_id = :id")
    @RegisterConstructorMapper(ExportFilter.class)
    Optional<ExportFilter> findFilterById(@Bind("id") long id);

    @SqlQuery("select * from export_filter where export_activity_id")
    @RegisterConstructorMapper(ExportFilter.class)
    Optional<List<ExportFilter>> findFiltersByActivityId(@Bind("activityId") long activityId);

    @SqlQuery("select * from export_activity_status_filter where export_activity_status_filter_id = :id")
    @RegisterConstructorMapper(ExportActivityStatusFilter.class)
    Optional<ExportActivityStatusFilter> findActivityStatusFilterById(@Bind("id") long id);

    @SqlQuery("select * from export_activity_status_filter where filter_id = :id")
    @RegisterConstructorMapper(ExportActivityStatusFilter.class)
    Optional<List<ExportActivityStatusFilter>> findActivityStatusFiltersByFilterId(@Bind("id") long id);


    // Delete
    @SqlUpdate("delete from configured_export where configured_export_id = :id")
    void deleteConfiguredExportById(@Bind("id") long id);

    @SqlUpdate("delete from excluded_participant_field where configured_export_id = :configuredExportId")
    void deleteExcludedParticipantFieldsByExportId(@Bind("configuredExportId") long configuredExportId);

    @SqlUpdate("delete from export_activity where configured_export_id = :configuredExportId")
    void deleteExportActivitiesByExportId(@Bind("configuredExportId") long configuredExportId);

    @SqlUpdate("delete from excluded_activity_field where export_activity_id = (select export_activity_id from export_activity where "
            + "configured_export_id = :id)")
    void deleteExcludedActivityFieldsByExportId(@Bind("id") long id);

    @SqlUpdate("delete from excluded_metadata_field where export_activity_id = (select export_activity_id from export_activity where "
            + "configured_export_id = :id)")
    void deleteExcludedMetadataFieldsByExportId(@Bind("id") long id);

    @SqlUpdate("delete from export_filter where export_activity_id = (select export_activity_id from export_activity "
            + "where configured_export_id = :id)")
    void deleteExportFiltersByExportId(@Bind("id") long id);

    @SqlUpdate("delete from export_activity_status_filter where export_filter_id = (select export_filter_id from export_filter where "
            + "export_activity_id = (select export_activity_id from export_activity where configured_export_id = :id))")
    void deleteExportActivityStatusFiltersByExportId(@Bind("id") long id);

    @SqlUpdate("delete from export_first_field where export_activity_id = (select export_activity_id from export_activity where "
            + "configured_export_id = :id)")
    void deleteExportFirstFieldsByExportId(@Bind("id") long id);
}
