package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ActivityConditionDto {

    private long studyActivityId;
    private long creationExpressionId;
    private String creationExpression;

    public ActivityConditionDto(
            long studyActivityId,
            long creationExpressionId,
            String creationExpression
    ) {
        this.studyActivityId = studyActivityId;
        this.creationExpressionId = creationExpressionId;
        this.creationExpression = creationExpression;
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }

    public long getCreationExpressionId() {
        return creationExpressionId;
    }

    public String getCreationExpression() {
        return creationExpression;
    }

    public static class ActivityConditionDtoMapper implements RowMapper<ActivityConditionDto> {
        @Override
        public ActivityConditionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ActivityConditionDto(
                    rs.getLong(SqlConstants.ActivityConditionTable.STUDY_ACTIVITY_ID),
                    rs.getLong(SqlConstants.ActivityConditionTable.CREATION_EXPRESSION_ID),
                    rs.getString(SqlConstants.ActivityConditionTable.CREATION_EXPRESSION)
            );
        }
    }
}
