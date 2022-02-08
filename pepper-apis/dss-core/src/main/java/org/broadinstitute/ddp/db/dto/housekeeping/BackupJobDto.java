package org.broadinstitute.ddp.db.dto.housekeeping;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class BackupJobDto {

    private String runName;
    private long startTime;
    private Long endTime;
    private String databaseName;
    private String status;

    @JdbiConstructor
    public BackupJobDto(@ColumnName("run_name") String runName,
                        @ColumnName("start_time") long startTime,
                        @ColumnName("end_time") Long endTime,
                        @ColumnName("database_name") String databaseName,
                        @ColumnName("status") String status) {
        this.runName = runName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.databaseName = databaseName;
        this.status = status;
    }

    public String getRunName() {
        return runName;
    }

    public long getStartTime() {
        return startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getStatus() {
        return status;
    }

}
