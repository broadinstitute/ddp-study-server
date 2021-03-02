package org.broadinstitute.ddp.db.dao;

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
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


public interface ConfiguredExportDao extends SqlObject {

    @CreateSqlObject
    ConfiguredExportSql getConfiguredExportSql();

    default ConfiguredExport createConfiguredExport(ConfiguredExport configuredExport) {
        long configuredExportId = getConfiguredExportSql().insertConfiguredExport(configuredExport.getStudyId(),
                configuredExport.getEnabled(), configuredExport.getRunSchedule(), configuredExport.getBucketType(),
                configuredExport.getBucketName(), configuredExport.getFilePath());

        return findConfiguredExportById(configuredExportId).orElseThrow(() ->
          new DaoException("Could not find newly created configured export with id " + configuredExportId));
    }

    default ExcludedParticipantField createExcludedParticipantField(ExcludedParticipantField field) {
        long excludedParticipantFieldId = getConfiguredExportSql()
                .insertExcludedParticipantFieldByStudyId(field.getConfiguredExportId(), field.getExcludedParticipantField());

        return findExcludedParticipantFieldById(excludedParticipantFieldId).orElseThrow(() ->
          new DaoException("Could not find newly created excluded participant field with id " + excludedParticipantFieldId));
    }

    default ExportActivity createExportActivity(ExportActivity activity) {
        long exportActivityId = getConfiguredExportSql()
                .insertExportActivityByStudyActivityIdAndCode(activity.getStudyActivityId(), activity.isIncremental(),
                  activity.getActivityCode());

        return findExportActivityById(exportActivityId).orElseThrow(() ->
          new DaoException("Could not find newly created export activity with id " + exportActivityId));
    }

    default ExcludedActivityField createExcludedActivityField(ExcludedActivityField field) {
        long excludedActivityFieldId = getConfiguredExportSql()
                .insertExcludedActivityFieldByActivityId(field.getExportActivityId(), field.getExcludedActivityField());

        return findExcludedActivityFieldById(excludedActivityFieldId).orElseThrow(() ->
          new DaoException("Could not find newly created excluded activity field with id " + excludedActivityFieldId));
    }

    default ExcludedMetadataField createExcludedMetadataField(ExcludedMetadataField field) {
        long excludedMetadataFieldId = getConfiguredExportSql()
                .insertExcludedMetadataFieldByActivityId(field.getExportActivityId(), field.getExcludedMetadataField());

        return findExcludedMetadataFieldById(excludedMetadataFieldId).orElseThrow(() ->
          new DaoException("Could not find newly created excluded metadata field with id " + excludedMetadataFieldId));
    }

    default ExportFirstField createExportFirstField(ExportFirstField field) {
        long exportFirstFieldId = getConfiguredExportSql()
                .insertExportFirstFieldByActivityId(field.getActivityId(), field.getFirstField());

        return findFirstFieldById(exportFirstFieldId).orElseThrow(() ->
          new DaoException("Could not find newly created excluded metadata field with id " + exportFirstFieldId));
    }

    default ExportFilter createExportFilter(ExportFilter filter) {
        long exportFilterId = getConfiguredExportSql()
                .insertExportFilterByActivityId(filter.getExportActivityId(), filter.getFilterType());

        return findFilterById(exportFilterId).orElseThrow(() ->
          new DaoException("Could not find newly created export filter with id " + exportFilterId));
    }

    default ExportActivityStatusFilter createExportActivityStatusFilter(ExportActivityStatusFilter filter) {
        long exportActivityStatusFilterId = getConfiguredExportSql()
                .insertExportActivityStatusFilterByExportFilterId(filter.getFilterId(), filter.getStatusType());

        return findActivityStatusFilterById(exportActivityStatusFilterId).orElseThrow(() ->
          new DaoException("Could not find newly created export activity status filter with id " + exportActivityStatusFilterId));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("findConfiguredExportById")
    @RegisterConstructorMapper(ConfiguredExport.class)
    Optional<ConfiguredExport> findConfiguredExportById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findExcludedParticipantFieldById")
    @RegisterConstructorMapper(ExcludedParticipantField.class)
    Optional<ExcludedParticipantField> findExcludedParticipantFieldById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findExportActivityById")
    @RegisterConstructorMapper(ExportActivity.class)
    Optional<ExportActivity> findExportActivityById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findExcludedActivityFieldById")
    @RegisterConstructorMapper(ExcludedActivityField.class)
    Optional<ExcludedActivityField> findExcludedActivityFieldById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findExcludedMetadataFieldById")
    @RegisterConstructorMapper(ExcludedMetadataField.class)
    Optional<ExcludedMetadataField> findExcludedMetadataFieldById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findFirstFieldById")
    @RegisterConstructorMapper(ExportFirstField.class)
    Optional<ExportFirstField> findFirstFieldById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findFilterById")
    @RegisterConstructorMapper(ExportFilter.class)
    Optional<ExportFilter> findFilterById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findStatusFilterById")
    @RegisterConstructorMapper(ExportActivityStatusFilter.class)
    Optional<ExportActivityStatusFilter> findActivityStatusFilterById(@Bind("id") long id);
}
