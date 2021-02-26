package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public class ConfiguredExport {
  private long id;
  private long studyId;
  private Boolean isEnabled;
  private String runSchedule;
  private String bucketType;
  private String bucketName;
  private String filePath;

  @JdbiConstructor
  public ConfiguredExport( @ColumnName("configured_export_id") long id,
                           @ColumnName("study_id") long studyId,
                           @ColumnName("is_enabled") Boolean isEnabled,
                           @ColumnName("run_schedule") String runSchedule,
                           @ColumnName("bucket_type") String bucketType,
                           @ColumnName("bucket_name") String bucketName,
                           @ColumnName("file_path") String filePath) {
    this.id = id;
    this.studyId = studyId;
    this.isEnabled = isEnabled;
    this.runSchedule = runSchedule;
    this.bucketType = bucketType;
    this.bucketName = bucketName;
    this.filePath = filePath;
  }

  public ConfiguredExport(long studyId, Boolean isEnabled, String runSchedule, String bucketType,
                          String bucketName, String filePath) {
    this.studyId = studyId;
    this.isEnabled = isEnabled;
    this.runSchedule = runSchedule;
    this.bucketType = bucketType;
    this.bucketName = bucketName;
    this.filePath = filePath;
  }

  public long getId() {
    return id;
  }

  public long getStudyId() {
    return studyId;
  }

  public Boolean getEnabled() {
    return isEnabled;
  }

  public String getRunSchedule() {
    return runSchedule;
  }

  public String getBucketType() {
    return bucketType;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getFilePath() {
    return filePath;
  }
}
