package org.broadinstitute.ddp.db.housekeeping.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.housekeeping.BackupJobDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBackupJob extends SqlObject {

    @SqlUpdate("insert into backup_job(run_name, start_time, end_time, database_name, status) "
            + " values(:runName, :startTime,:endTime,:databaseName, :status)")
    @GetGeneratedKeys
    long insert(@Bind("runName") String runName,
                @Bind("startTime") long startTime,
                @Bind("endTime") Long endTime,
                @Bind("databaseName") String databaseName,
                @Bind("status") String status);

    @SqlUpdate("update backup_job set end_time = :endTime, status = :status where run_name = :runName")
    int updateEndTimeStatus(@Bind("runName") String runName, @Bind("endTime") Long endTime, @Bind("status") String status);

    @SqlQuery("select run_name, start_time, end_time, database_name, status from backup_job where status = 'PENDING' ")
    @RegisterConstructorMapper(BackupJobDto.class)
    List<BackupJobDto> getPendingBackupJobs();

    @SqlQuery("select run_name, start_time, end_time, database_name, status from backup_job where run_name = :runName")
    @RegisterConstructorMapper(BackupJobDto.class)
    Optional<BackupJobDto> getBackupJob(@Bind("runName") String runName);

}
