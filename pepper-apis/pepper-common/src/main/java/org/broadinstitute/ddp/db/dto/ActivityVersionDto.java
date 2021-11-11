package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.ActivityVersionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.RevisionTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ActivityVersionDto {

    private long id;
    private long activityId;
    private String versionTag;
    private long revId;
    private long revStart;
    private Long revEnd;

    public ActivityVersionDto(long id, long activityId, String versionTag, long revId, long revStart, Long revEnd) {
        this.id = id;
        this.activityId = activityId;
        this.versionTag = versionTag;
        this.revId = revId;
        this.revStart = revStart;
        this.revEnd = revEnd;
    }

    public long getId() {
        return id;
    }

    public long getActivityId() {
        return activityId;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public long getRevId() {
        return revId;
    }

    public long getRevStart() {
        return revStart;
    }

    public Long getRevEnd() {
        return revEnd;
    }

    public static class ActivityVersionDtoMapper implements RowMapper<ActivityVersionDto> {
        @Override
        public ActivityVersionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ActivityVersionDto(
                    rs.getLong(ActivityVersionTable.ID),
                    rs.getLong(ActivityVersionTable.ACTIVITY_ID),
                    rs.getString(ActivityVersionTable.TAG),
                    rs.getLong(RevisionTable.ID),
                    rs.getLong(RevisionTable.START_DATE),
                    (Long) rs.getObject(RevisionTable.END_DATE));
        }
    }
}
