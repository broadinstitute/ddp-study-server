package org.broadinstitute.ddp.db.dao;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.broadinstitute.ddp.model.activity.instance.validation.DateRangeRule;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
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

    default DateRangeRule findRuleByIdWithMessage(long validationId, String defaultMessage, String correctionHint, boolean allowSave) {
        String sql = "select * from date_range_validation where validation_id = :id";
        return getHandle().createQuery(sql)
                .bind("id", validationId)
                .map(new DateRangeWithMessageMapper(defaultMessage, correctionHint, allowSave))
                .findOnly();
    }

    class DateRangeWithMessageMapper implements RowMapper<DateRangeRule> {
        private String defaultMessage;
        private String correctionHint;
        private boolean allowSave;

        DateRangeWithMessageMapper(String defaultMessage, String correctionHint, boolean allowSave) {
            this.defaultMessage = defaultMessage;
            this.correctionHint = correctionHint;
            this.allowSave = allowSave;
        }

        @Override
        public DateRangeRule map(ResultSet rs, StatementContext ctx) throws SQLException {
            Date start = rs.getDate("start_date");
            LocalDate startDate = (start == null ? null : start.toLocalDate());

            Date end = rs.getDate("end_date");
            LocalDate endDate = (end == null ? null : end.toLocalDate());

            boolean useTodayAsEnd = rs.getBoolean("use_today_as_end");
            if (useTodayAsEnd) {
                endDate = LocalDate.now(ZoneOffset.UTC);
            }

            return DateRangeRule.of(rs.getLong("validation_id"), defaultMessage, correctionHint, allowSave, startDate, endDate);
        }
    }
}
