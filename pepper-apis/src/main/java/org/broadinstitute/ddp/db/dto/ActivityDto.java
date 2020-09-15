package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class ActivityDto {

    private final long activityTypeId;
    private final long studyId;
    private final boolean instantiateUponRegistration;
    private String activityCode;
    private long activityId;
    private int displayOrder;
    private Integer maxInstancesPerUser;
    private Long editTimeoutSec;
    private boolean writeOnce;
    private boolean allowOndemandTrigger;
    private boolean isFollowup;
    private boolean hideInstances;

    /**
     * Instantiate an ActivityDto object.
     */
    public ActivityDto(
            long activityId,
            String activityCode,
            long activityTypeId,
            long studyId,
            int displayOrder,
            boolean instantiateUponRegistration,
            Integer maxInstancesPerUser,
            Long editTimeoutSec,
            boolean writeOnce,
            boolean allowOndemandTrigger,
            boolean isFollowup,
            boolean hideInstances
    ) {
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.activityTypeId = activityTypeId;
        this.studyId = studyId;
        this.displayOrder = displayOrder;
        this.instantiateUponRegistration = instantiateUponRegistration;
        this.maxInstancesPerUser = maxInstancesPerUser;
        this.editTimeoutSec = editTimeoutSec;
        this.writeOnce = writeOnce;
        this.allowOndemandTrigger = allowOndemandTrigger;
        this.isFollowup = isFollowup;
        this.hideInstances = hideInstances;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public void setActivityCode(String activityCode) {
        this.activityCode = activityCode;
    }

    public long getActivityId() {
        return activityId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Integer getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public long getActivityTypeId() {
        return activityTypeId;
    }

    public long getStudyId() {
        return studyId;
    }

    public boolean isInstantiateUponRegistration() {
        return instantiateUponRegistration;
    }

    public Long getEditTimeoutSec() {
        return editTimeoutSec;
    }

    public boolean isWriteOnce() {
        return writeOnce;
    }

    public boolean isOndemandTriggerAllowed() {
        return allowOndemandTrigger;
    }

    public boolean isHideInstances() {
        return hideInstances;
    }

    public static class ActivityRowMapper implements RowMapper<ActivityDto> {
        @Override
        public ActivityDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ActivityDto(rs.getLong(SqlConstants.StudyActivityTable.ID),
                    rs.getString(SqlConstants.StudyActivityTable.CODE),
                    rs.getLong(SqlConstants.StudyActivityTable.ACTIVITY_TYPE_ID),
                    rs.getLong(SqlConstants.StudyActivityTable.STUDY_ID),
                    rs.getInt(SqlConstants.StudyActivityTable.DISPLAY_ORDER),
                    rs.getBoolean(SqlConstants.StudyActivityTable.INSTANTIATE_UPON_REGISTRATION),
                    (Integer) rs.getObject(SqlConstants.StudyActivityTable.MAX_INSTANCES_PER_USER),
                    (Long) rs.getObject(SqlConstants.StudyActivityTable.EDIT_TIMEOUT_SEC),
                    rs.getBoolean(SqlConstants.StudyActivityTable.IS_WRITE_ONCE),
                    rs.getBoolean(SqlConstants.StudyActivityTable.ALLOW_ONDEMAND_TRIGGER),
                    rs.getBoolean(SqlConstants.StudyActivityTable.IS_FOLLOWUP),
                    rs.getBoolean(SqlConstants.StudyActivityTable.HIDE_INSTANCES));
        }
    }
}
