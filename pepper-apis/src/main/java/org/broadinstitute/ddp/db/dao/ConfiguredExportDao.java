package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.export.ConfiguredExport;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.Optional;

public interface ConfiguredExportDao extends SqlObject {

  @CreateSqlObject
  ConfiguredExportSql getConfiguredExportSql();

  default ConfiguredExport createConfiguredExport(ConfiguredExport configuredExport) {
    long configuredExportId = getConfiguredExportSql().insertConfiguredExport(configuredExport.getStudyId(),
      configuredExport.getEnabled(), configuredExport.getRunSchedule(), configuredExport.getBucketType(),
      configuredExport.getBucketName(), configuredExport.getFilePath());

    return findConfiguredExportByConfiguredExportId(configuredExportId).orElseThrow(() ->
      new DaoException("Could not find newly created configured export with id " + configuredExportId));
  }

  @UseStringTemplateSqlLocator
  @SqlQuery("findConfiguredExportByConfiguredExportId")
  @RegisterConstructorMapper(ConfiguredExport.class)
  Optional<ConfiguredExport> findConfiguredExportByConfiguredExportId(@Bind("configuredExportId") long configuredExportId);

  //TODO
}
