package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.SqlConstants.ActivityInstanceTable;
import org.broadinstitute.ddp.constants.SqlConstants.ConsentConditionTable;
import org.broadinstitute.ddp.constants.SqlConstants.StudyActivityTable;
import org.broadinstitute.ddp.constants.SqlFile.StudyActivitySql;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.JdbiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyActivityDao {

    private static final Logger LOG = LoggerFactory.getLogger(StudyActivityDao.class);

    private static String AUTO_INSTANTIATABLE_ACTIVITIES_BY_CLIENT_ID_QUERY;
    private static String ACTIVITY_CODE_BY_STUDY_GUID_AND_FORM_TYPE_QUERY;
    private static String ALL_CONSENTS_BY_USER_AND_STUDY_GUIDS_QUERY;
    private static String CONSENT_BY_GUIDS_QUERY;

    public StudyActivityDao() {
    }

    /**
     * Load necessary sql commands from given config.
     *
     * @param sqlConfig the config object with sql commands
     */
    public static void loadSqlCommands(Config sqlConfig) {
        AUTO_INSTANTIATABLE_ACTIVITIES_BY_CLIENT_ID_QUERY
                = sqlConfig.getString(StudyActivitySql.AUTO_INSTANTIATABLE_ACTIVITIES_BY_CLIENT_ID_QUERY);
        ACTIVITY_CODE_BY_STUDY_GUID_AND_FORM_TYPE_QUERY
                = sqlConfig.getString(StudyActivitySql.ACTIVITY_CODE_BY_STUDY_GUID_AND_FORM_TYPE_QUERY);
        ALL_CONSENTS_BY_USER_AND_STUDY_GUIDS_QUERY
                = sqlConfig.getString(StudyActivitySql.ALL_CONSENTS_BY_USER_AND_STUDY_GUIDS_QUERY);
        CONSENT_BY_GUIDS_QUERY = sqlConfig.getString(StudyActivitySql.CONSENT_BY_GUIDS_QUERY);
    }

    /**
     * Returns a list of ids of the study activities eligible for instantiation upon user registration.
     * A client must have access to the studies these activities pertain to.
     *
     * @param handle   the database handle
     * @param clientId Id of the client to get activities for
     * @return A list of study activities' ids
     */
    public List<Long> getAutoInstantiatableActivityIdsByClientId(Handle handle, Long clientId) {
        List<Long> autoInstantiatableActivities = new ArrayList<>();
        try (PreparedStatement stmt = handle.getConnection()
                .prepareStatement(AUTO_INSTANTIATABLE_ACTIVITIES_BY_CLIENT_ID_QUERY)) {
            stmt.setLong(1, clientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                autoInstantiatableActivities.add(rs.getLong(SqlConstants.STUDY_ACTIVITY_ID));
            }
        } catch (SQLException e) {
            throw new DaoException("Could not fetch auto-instantiatable activities for the client " + clientId, e);
        }
        return autoInstantiatableActivities;
    }

    /**
     * Get code for prequalifier activity of given study. There can be many per study in theory,
     * but we take just the one with the smallest display order at the moment
     *
     * @param handle    the jdbi handle
     * @param studyGuid the study guid
     * @return          prequalifier activity code, if found
     */
    public Optional<String> getPrequalifierActivityCodeForStudy(Handle handle, String studyGuid) {
        try {
            // We fetch all codes, but limit their number programmatically. No need to adjust the query
            // if the need to return all emerges in the future
            List<String> prequalCodes = handle.createQuery(ACTIVITY_CODE_BY_STUDY_GUID_AND_FORM_TYPE_QUERY)
                    .bind(0, studyGuid)
                    .bind(1, FormType.PREQUALIFIER.name())
                    .mapTo(String.class)
                    .list();
            String prequalCode = null;
            if (prequalCodes.size() == 1) {
                // Pick the prequalifier with the smallest display order
                prequalCode = prequalCodes.get(0);
            } else if (prequalCodes.size() > 1) {
                // An exception for now, can be different in the future
                String errMsg = "There can be 1 and only 1 prequalifier activity for the study, but "
                        + prequalCodes.size() + " prequalifier activities for study " + studyGuid + " were found, "
                        + "guids: " + prequalCodes.stream().collect(Collectors.joining(", "));
                throw new DaoException(errMsg);
            }
            return Optional.ofNullable(prequalCode);
        } catch (JdbiException e) {
            throw new DaoException("Could not find prequalifier activity guid for study guid " + studyGuid, e);
        }
    }

    /**
     * Get all consent activity summaries for given study. Note that elections are not returned.
     * If a consent activity has an associated instance for the user, that particular revision will
     * be fetched. If there isn't an instance, the latest revision of the consent is fetched.
     *
     * @param handle    the jdbi handle
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @return list of consents, or empty
     * @throws DaoException if sql error
     */
    public List<ConsentSummary> getAllConsentSummaries(Handle handle, String userGuid, String studyGuid) {
        List<ConsentSummary> summaries = new ArrayList<>();
        try (PreparedStatement stmt
                     = handle.getConnection().prepareStatement(ALL_CONSENTS_BY_USER_AND_STUDY_GUIDS_QUERY)) {
            stmt.setString(1, userGuid);
            stmt.setString(2, studyGuid);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String activityCode = rs.getString(StudyActivityTable.CODE);
                String instanceGuid = rs.getString(ActivityInstanceTable.GUID);
                String consentedExpr = rs.getString(ConsentConditionTable.CONSENTED_EXPRESSION);
                long activityId = rs.getLong(StudyActivityTable.ID);
                summaries.add(new ConsentSummary(activityId, activityCode, instanceGuid, consentedExpr));
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find consent summaries for user " + userGuid + " study " + studyGuid, e);
        }
        return summaries;
    }

    /**
     * Get specified consent activity for given study. Note that elections are not returned.
     * If the consent activity has an associated instance for the user, that particular revision
     * will be fetched. Otherwise, the latest revision will be fetched.
     *
     * @param handle       the jdbi handle
     * @param userGuid     the user guid
     * @param studyGuid    the study guid
     * @param consentActivityCode the consent activity stable code
     * @return the latest consent summary, if found
     * @throws DaoException if sql error
     */
    public Optional<ConsentSummary> getLatestConsentSummary(Handle handle, String userGuid,
                                                      String studyGuid, String consentActivityCode) {
        Optional<ConsentSummary> summary = Optional.empty();
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(CONSENT_BY_GUIDS_QUERY)) {
            stmt.setString(1, userGuid);
            stmt.setString(2, studyGuid);
            stmt.setString(3, consentActivityCode);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String instanceGuid = rs.getString(ActivityInstanceTable.GUID);
                String consentedExpr = rs.getString(ConsentConditionTable.CONSENTED_EXPRESSION);
                long activityId = rs.getLong(StudyActivityTable.ID);
                summary = Optional.of(new ConsentSummary(activityId, consentActivityCode, instanceGuid, consentedExpr));
            } else {
                LOG.info("No consent summary found for user {} study {} activity {}",
                        userGuid, studyGuid, consentActivityCode);
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find consent summary for user " + userGuid
                    + " study " + studyGuid + " activity " + consentActivityCode, e);
        }
        return summary;
    }
}
