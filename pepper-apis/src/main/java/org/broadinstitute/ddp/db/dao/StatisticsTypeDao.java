package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.model.statistics.StatisticsType;
import org.broadinstitute.ddp.model.statistics.StatisticsTypes;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface StatisticsTypeDao extends SqlObject {

    @SqlQuery("SELECT statistics_type_id, statistics_type_code FROM statistics_type WHERE statistics_type_code = :statisticsTypeName")
    @RegisterRowMapper(StatisticsTypeRowMapper.class)
    Optional<StatisticsType> getKitTypeByType(@Bind String statisticsTypeName);

    @SqlQuery("SELECT statistics_type_id, statistics_type_code FROM statistics_type WHERE statistics_type_id = :id")
    @RegisterRowMapper(StatisticsTypeRowMapper.class)
    Optional<StatisticsType> getKitTypeById(@Bind Long id);

    class StatisticsTypeRowMapper implements RowMapper<StatisticsType> {
        @Override
        public StatisticsType map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new StatisticsType(rs.getLong(SqlConstants.StatisticsTypeTable.ID),
                    StatisticsTypes.valueOf(rs.getString(SqlConstants.StatisticsTypeTable.CODE)));
        }
    }

}
