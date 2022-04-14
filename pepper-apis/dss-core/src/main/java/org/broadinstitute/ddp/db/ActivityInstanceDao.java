package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable;
import org.broadinstitute.ddp.constants.SqlConstants.LanguageCodeTable;
import org.broadinstitute.ddp.constants.SqlFile.ActivityInstanceSql;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.json.UserActivity;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.util.I18nUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.JdbiException;
import org.jdbi.v3.core.generic.GenericType;

public class ActivityInstanceDao {
    private static String TRANSLATED_SUMMARY_BY_GUID_QUERY;
    private static String INSTANCE_ID_BY_GUID_QUERY;
    private static String SECTIONS_SIZE_FOR_ACTIVITY_INSTANCE;

    /**
     * Given {@code sqlConfig}, load relevant SQL queries.
     */
    public static void loadSqlCommands(Config sqlConfig) {
        TRANSLATED_SUMMARY_BY_GUID_QUERY = sqlConfig.getString(ActivityInstanceSql.TRANSLATED_SUMMARY_BY_GUID_QUERY);
        INSTANCE_ID_BY_GUID_QUERY = sqlConfig.getString(ActivityInstanceSql.INSTANCE_ID_BY_GUID_QUERY);
        SECTIONS_SIZE_FOR_ACTIVITY_INSTANCE = sqlConfig.getString(ActivityInstanceSql.SECTIONS_SIZE_FOR_ACTIVITY_INSTANCE);
    }

    /**
     * Given a list of study activities, get the guid of the latest user's activity instance
     *
     * @param handle       the jdbi handle
     * @param userGuid     the participant user guid
     * @param activityCode the study activity stable code
     * @return instance guid, if found
     */
    public Optional<String> getGuidOfLatestInstanceForUserAndActivity(Handle handle, String userGuid, String activityCode,
                                                                      long studyId) {
        try {
            return handle.attach(JdbiActivityInstance.class)
                    .findLatestInstanceGuidByUserGuidAndCodesOfActivities(userGuid, List.of(activityCode), studyId);
        } catch (IllegalStateException e) {
            throw new DaoException("Multiple rows found for user guid " + userGuid
                    + " and activity code " + activityCode, e);
        } catch (JdbiException e) {
            throw new DaoException("Could not find instance guid for user guid " + userGuid
                    + " and activity code " + activityCode, e);
        }
    }

    /**
     * Get summary representation of specific activity instance translated into the preferred language
     *
     * @param handle               the jdbi handle
     * @param activityInstanceGuid the activity instance guid
     * @return activity summary, if found
     */
    public UserActivity getTranslatedSummaryByGuid(
            Handle handle,
            String activityInstanceGuid,
            String preferredLanguageCode
    ) {
        try {
            List<UserActivity> userActivities = handle.createQuery(TRANSLATED_SUMMARY_BY_GUID_QUERY)
                    .bind(0, activityInstanceGuid)
                    .registerRowMapper(UserActivity.class, (rs, ctx) -> {
                        String title = rs.getString(ActivityInstanceTable.TITLE);
                        String subtitle = rs.getString(ActivityInstanceTable.SUBTITLE);
                        String type = rs.getString(ActivityInstanceTable.TYPE_NAME);
                        String status = rs.getString(ActivityInstanceTable.STATUS_TYPE_NAME);
                        String isoLangCode = rs.getString(LanguageCodeTable.CODE);
                        return new UserActivity(activityInstanceGuid, title, subtitle, type, status, isoLangCode);
                    })
                    .collectInto(new GenericType<>() {
                    });
            // Picking the appropriate language for each activity instance
            List<UserActivity> userActivitiesTranslated = I18nUtil.getActivityInstanceTranslation(
                    userActivities, preferredLanguageCode
            );
            Optional<UserActivity> userActivityTranslated = Optional.ofNullable(
                    userActivitiesTranslated.isEmpty() ? null : userActivitiesTranslated.get(0)
            );
            return userActivityTranslated.orElseThrow(
                    () -> new DaoException("Unable to find translated summary for prequalifier instance " + activityInstanceGuid)
            );
        } catch (IllegalStateException e) {
            throw new DaoException("Multiple rows found for instance guid " + activityInstanceGuid
                    + " iso lang " + preferredLanguageCode, e);
        } catch (JdbiException e) {
            throw new DaoException("Could not find summary for instance guid " + activityInstanceGuid
                    + " iso lang " + preferredLanguageCode, e);
        }
    }

    /**
     * Given an activity instance GUID, returns a activity instance id.
     *
     * @param handle JDBC connection
     * @param guid   GUID of the activity instance to get an id for
     * @return Id of the activity instance
     */
    public Long getActivityInstanceIdByGuid(Handle handle, String guid) {
        long activityInstanceId;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(INSTANCE_ID_BY_GUID_QUERY)) {
            stmt.setString(1, guid);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new NoSuchElementException("Could not fetch an activity instance with GUID " + guid);
            }

            activityInstanceId = rs.getLong(SqlConstants.ACTIVITY_INSTANCE_ID);

            if (rs.next()) {
                throw new RuntimeException("The number of returned records for the activity instance with GUID "
                        + guid + " > 1");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Could not fetch an activity instance", e);
        }
        return activityInstanceId;
    }

    /**
     * Given a user, return a collection of activity instance summaries.
     *
     * @param handle       JDBC handle
     * @param userGuid     GUID of the user
     * @param studyGuid    GUID of the study
     * @param instanceGuid GUID of the activity instance
     * @return A size of sections for activity instance
     */
    public int getActivityInstanceSectionsSize(Handle handle,
                                               String userGuid,
                                               String studyGuid,
                                               String instanceGuid) {
        int result = 0;
        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(SECTIONS_SIZE_FOR_ACTIVITY_INSTANCE)) {
                stmt.setString(1, studyGuid);
                stmt.setString(2, userGuid);
                stmt.setString(3, instanceGuid);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String activityCode = rs.getString(SqlConstants.StudyActivityTable.CODE);
                    String versionTag = rs.getString(SqlConstants.ActivityVersionTable.TAG);
                    ActivityDefStore activityDefStore = ActivityDefStore.getInstance();
                    FormActivityDef formActivityDef = activityDefStore.getActivityDef(studyGuid, activityCode, versionTag);
                    if (formActivityDef == null) {
                        long versionId = rs.getLong(SqlConstants.ActivityVersionTable.REVISION_ID);
                        long revisionStart = rs.getLong(SqlConstants.RevisionTable.START_DATE);
                        Optional<ActivityDto> activityDto = handle.attach(JdbiActivity.class)
                                .findActivityByStudyGuidAndCode(studyGuid, activityCode);
                        formActivityDef = handle.attach(FormActivityDao.class).findDefByDtoAndVersion(
                                activityDto.get(), versionTag, versionId, revisionStart);
                        activityDefStore.setActivityDef(studyGuid, activityCode, versionTag, formActivityDef);
                    }
                    result = formActivityDef.getAllSections().size();
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not lookup activity instances for user " + userGuid, e);
        }
        return result;
    }

}
