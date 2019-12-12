package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.json.activity.ActivityInstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiActivityInstanceStatusType extends SqlObject {

    @SqlQuery("select activity_instance_status_type_id from activity_instance_status_type "
            + "where activity_instance_status_type_code = :statusType")
    long getStatusTypeId(@Bind("statusType")InstanceStatusType statusType);

    @SqlQuery("queryTranslatedActivityInstanceStatusTypeNames")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(ActivityInstanceStatusTypeMapper.class)
    List<ActivityInstanceStatusType> getActivityInstanceStatusTypes(@Bind String isoLanguageCode);

    class ActivityInstanceStatusTypeMapper implements RowMapper<ActivityInstanceStatusType> {
        public ActivityInstanceStatusType map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ActivityInstanceStatusType(
                    rs.getString(SqlConstants.ActivityInstanceStatusTypeTable.ACTIVITY_STATUS_TYPE_CODE),
                    rs.getString(SqlConstants.ActivityInstanceStatusTypeTable.TYPE_NAME)
            );
        }
    }
}
