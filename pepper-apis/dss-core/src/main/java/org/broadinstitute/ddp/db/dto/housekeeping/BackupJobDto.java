package org.broadinstitute.ddp.db.dto.housekeeping;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BackupJobDto {
    @ColumnName("run_name")
    private String runName;

    @ColumnName("start_time")
    private long startTime;

    @ColumnName("end_time")
    private Long endTime;

    @ColumnName("database_name")
    private String databaseName;

    @ColumnName("status")
    private String status;
}
