package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.SqlConstants.ConsentElectionTable;
import org.broadinstitute.ddp.constants.SqlFile.ConsentElectionSql;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.jdbi.v3.core.Handle;

public class ConsentElectionDao {

    private static String ELECTIONS_BY_ACTIVITY_AND_INSTANCE_GUIDS_QUERY;
    private static String LATEST_ELECTIONS_BY_ACTIVITY_CODE_QUERY;

    public ConsentElectionDao() {
    }

    /**
     * Load necessary sql commands from given config.
     *
     * @param sqlConfig the config object with sql commands
     */
    public static void loadSqlCommands(Config sqlConfig) {
        LATEST_ELECTIONS_BY_ACTIVITY_CODE_QUERY
                = sqlConfig.getString(ConsentElectionSql.LATEST_ELECTIONS_BY_ACTIVITY_CODE_QUERY);
        ELECTIONS_BY_ACTIVITY_AND_INSTANCE_GUIDS_QUERY
                = sqlConfig.getString(ConsentElectionSql.ELECTIONS_BY_ACTIVITY_AND_INSTANCE_GUIDS_QUERY);
    }

    /**
     * Get latest revision of all elections for given consent activity.
     *
     * @param handle              the jdbi handle
     * @param consentActivityCode the consent activity stable code
     * @return list of elections, or empty
     * @throws DaoException if sql error
     */
    public List<ConsentElection> getLatestElections(Handle handle, String consentActivityCode, long studyId) {
        List<ConsentElection> elections = new ArrayList<>();
        try (PreparedStatement stmt = handle.getConnection()
                .prepareStatement(LATEST_ELECTIONS_BY_ACTIVITY_CODE_QUERY)) {
            stmt.setString(1, consentActivityCode);
            stmt.setLong(2, studyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String stableId = rs.getString(ConsentElectionTable.STABLE_ID);
                String selectedExpr = rs.getString(ConsentElectionTable.SELECTED_EXPRESSION);
                elections.add(new ConsentElection(stableId, selectedExpr));
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find latest elections for consent activity " + consentActivityCode, e);
        }
        return elections;
    }

    /**
     * Get all elections for given consent activity at revision specified by given activity instance.
     *
     * @param handle              the jdbi handle
     * @param consentActivityCode the consent activity stable code
     * @param instanceGuid        the activity instance guid
     * @return list of elections, or empty
     * @throws DaoException if sql error
     */
    public List<ConsentElection> getElections(Handle handle, String consentActivityCode, String instanceGuid, long studyId) {
        List<ConsentElection> elections = new ArrayList<>();
        try (PreparedStatement stmt
                     = handle.getConnection().prepareStatement(ELECTIONS_BY_ACTIVITY_AND_INSTANCE_GUIDS_QUERY)) {
            stmt.setString(1, instanceGuid);
            stmt.setString(2, consentActivityCode);
            stmt.setLong(3, studyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String stableId = rs.getString(ConsentElectionTable.STABLE_ID);
                String selectedExpr = rs.getString(ConsentElectionTable.SELECTED_EXPRESSION);
                elections.add(new ConsentElection(stableId, selectedExpr));
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find elections for consent activity "
                    + consentActivityCode + " and instance " + instanceGuid, e);
        }
        return elections;
    }
}
