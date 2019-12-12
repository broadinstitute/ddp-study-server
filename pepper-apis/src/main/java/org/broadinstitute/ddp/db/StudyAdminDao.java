package org.broadinstitute.ddp.db;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.exception.FireCloudException;
import org.broadinstitute.ddp.json.export.ListStudiesResponse;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyAdminDao {

    private static final Logger LOG = LoggerFactory.getLogger(StudyAdminDao.class);
    private static String participantCount = "count_par";
    private String getStudyNamesForUserAdminGuidQuery;
    private String getStudyParticipantCountQuery;
    private String getServiceAccountPathWithStudyQuery;
    private String getServiceAccountPathWithoutStudyQuery;
    private File fcKeysDir;

    /**
     * Set up sql queries and FC directory location.
     */
    public StudyAdminDao(
            String getStudyNamesForUserAdminGuidQuery,
            String getStudyParticipantCountQuery,
            String getServiceAccountPathWithStudyQuery,
            String getServiceAccountPathWithoutStudyQuery,
            File fcKeysDir) {
        this.getStudyNamesForUserAdminGuidQuery = getStudyNamesForUserAdminGuidQuery;
        this.getStudyParticipantCountQuery = getStudyParticipantCountQuery;
        this.getServiceAccountPathWithStudyQuery = getServiceAccountPathWithStudyQuery;
        this.getServiceAccountPathWithoutStudyQuery = getServiceAccountPathWithoutStudyQuery;
        this.fcKeysDir = fcKeysDir;

        if (this.fcKeysDir != null) {
            LOG.info("Will use {} for firecloud keys.  It has {} keys.",
                    fcKeysDir.getAbsolutePath(), fcKeysDir.list().length);
        } else {
            LOG.warn("No firecloud keys directory given");
        }
    }

    /**
     * initialize StudyAdminDao.
     * @param sqlConfig the sql configuration
     * @param fcKeysDir file where FC key are located
     * @return a new instance of StudyAdminDao
     */
    public static StudyAdminDao init(Config sqlConfig, File fcKeysDir) {
        return new StudyAdminDao(
                sqlConfig.getString(SqlConstants.FireCloud.STUDY_NAMES_FOR_ADMIN_GUID),
                sqlConfig.getString(SqlConstants.FireCloud.STUDY_PARTICIPANT_COUNT),
                sqlConfig.getString(SqlConstants.FireCloud.SERVICE_ACCOUNT_PATH_WITH_STUDY_QUERY),
                sqlConfig.getString(SqlConstants.FireCloud.SERVICE_ACCOUNT_PATH_WITHOUT_STUDY_QUERY),
                fcKeysDir);
    }

    /**
     * given a database connection and the guid of a user, get all studies they have admin permission over.
     *
     * @param handle     the database connection
     * @param userGuid user guid
     * @return an arraylist holding the payload of each study (study_id, study_name, number of participants in study)
     */
    public Collection<ListStudiesResponse> getStudies(Handle handle, String userGuid) {
        Collection<ListStudiesResponse> listOfStudies = new ArrayList<>();

        List<String> studyNames = new ArrayList<>();
        List<String> studyGuids = new ArrayList<>();
        List<Long> umbrellaStudyIds = new ArrayList<>();

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(getStudyNamesForUserAdminGuidQuery)) {
            stmt.setString(1, userGuid);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                studyNames.add(rs.getString(SqlConstants.UmbrellaStudyTable.STUDY_NAME));
                studyGuids.add(rs.getString(SqlConstants.UmbrellaStudyTable.GUID));
                umbrellaStudyIds.add(rs.getLong(SqlConstants.UmbrellaStudyTable.UMBRELLA_STUDY_ID));
            }
        } catch (SQLException e) {
            throw new DaoException("Cannot find studies for user guid " + userGuid, e);
        }

        for (int i = 0; i < umbrellaStudyIds.size(); i++) {
            Long umbrellaStudyId = umbrellaStudyIds.get(i);
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(getStudyParticipantCountQuery)) {
                stmt.setLong(1, umbrellaStudyId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int participantCount = rs.getInt(StudyAdminDao.participantCount);
                    listOfStudies.add(new ListStudiesResponse(studyNames.get(i), studyGuids.get(i), participantCount));
                }

            } catch (SQLException e) {
                throw new RuntimeException("Cannot get count of participants in given study " + umbrellaStudyId, e);
            }
        }
        return listOfStudies;
    }

    private String getFullPathToFirecloudKey(String relativePath) {
        return new File(fcKeysDir, relativePath).getAbsolutePath();
    }

    /**
     * Given a user and study guid, return the location in pepper where their FireCloud permissions are.
     *
     * @param handle      the database connection
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @return file location in pepper where FireCloud permissions are
     */
    public String getServiceAccountPath(Handle handle, String userGuid, String studyGuid) {
        String path = null;
        if (studyGuid != null) {
            try (PreparedStatement stmt
                         = handle.getConnection().prepareStatement(getServiceAccountPathWithStudyQuery)) {
                stmt.setString(1, userGuid);
                stmt.setString(2, studyGuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    path = getFullPathToFirecloudKey(rs.getString(
                            SqlConstants.FirecloudServiceAccountTable.ACCOUNT_KEY_LOCATION));
                }
            } catch (SQLException e) {
                throw new DaoException("Cannot find path for service account for user "
                        + userGuid + " and study " + studyGuid, e);
            }
        } else {
            try (PreparedStatement stmt
                         = handle.getConnection().prepareStatement(getServiceAccountPathWithoutStudyQuery)) {
                stmt.setString(1, userGuid);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    path = getFullPathToFirecloudKey(rs.getString(
                            SqlConstants.FirecloudServiceAccountTable.ACCOUNT_KEY_LOCATION));
                } else {
                    throw new FireCloudException("User " + userGuid + "  is not an administrator of any studies.");
                }
            } catch (SQLException e) {
                throw new DaoException("Cannot find a path for service account for user " + userGuid);
            }
        }
        return path;
    }
}
