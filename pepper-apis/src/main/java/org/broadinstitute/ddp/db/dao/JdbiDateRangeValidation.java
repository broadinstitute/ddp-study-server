package org.broadinstitute.ddp.db.dao;

import java.time.LocalDate;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDateRangeValidation extends SqlObject {

    @SqlUpdate("insert into date_range_validation (validation_id, start_date, end_date, use_today_as_end)"
            + " values (:validationId, :startDate, :endDate, :useTodayAsEnd)")
    int insert(@Bind("validationId") long validationId,
               @Bind("startDate") LocalDate startDate,
               @Bind("endDate") LocalDate endDate,
               @Bind("useTodayAsEnd") boolean useTodayAsEnd);

}
