package org.broadinstitute.dsm.db.dao.tag.cohort;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CohortTagDaoImpl implements CohortTagDao {

    private static final Logger logger = LoggerFactory.getLogger(CohortTagDaoImpl.class);

    private static final String SQL_INSERT_COHORT_TAG =
            "INSERT INTO cohort_tag(cohort_tag_name, ddp_participant_id, ddp_instance_id, created, created_by) VALUES (?,?,?,?,?)";
    private static final String SQL_DELETE_COHORT_TAG_BY_ID = "DELETE FROM cohort_tag WHERE cohort_tag_id = ?";

    private static final String SQL_GET_TAGS_BY_INSTANCE_NAME = "SELECT * FROM cohort_tag WHERE ddp_instance_id = "
            + "(SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)";

    public static final String COHORT_TAG_ID = "cohort_tag_id";
    public static final String COHORT_TAG_NAME = "cohort_tag_name";
    public static final String COHORT_DDP_PARTICIPANT_ID = DBConstants.DDP_PARTICIPANT_ID;
    public static final String COHORT_DDP_INSTANCE_ID = DBConstants.DDP_INSTANCE_ID;

    @Override
    public int create(CohortTag cohortTagDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_COHORT_TAG, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, cohortTagDto.getCohortTagName());
                stmt.setString(2, cohortTagDto.getDdpParticipantId());
                stmt.setInt(3, cohortTagDto.getDdpInstanceId());
                stmt.setLong(4, System.currentTimeMillis());
                stmt.setString(5, cohortTagDto.getCreatedBy());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException(
                    String.format("Error inserting cohort tag for participant with id: %s", cohortTagDto.getDdpParticipantId()),
                    simpleResult.resultException);
        }
        logger.info(
                String.format(
                        "Cohort tag: %s has been created successfully for participant with id: %s",
                        cohortTagDto.getCohortTagName(), cohortTagDto.getDdpParticipantId()
                ));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_COHORT_TAG_BY_ID)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting cohort tag with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<CohortTag> get(long id) {
        return Optional.empty();
    }

    @Override
    public Map<String, List<CohortTag>> getCohortTagsByInstanceName(String instanceName) {
        Map<String, List<CohortTag>> result = new HashMap<>();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_TAGS_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                ResultSet resultSet = stmt.executeQuery();
                while (resultSet.next()) {
                    String ddpParticipantId = resultSet.getString(COHORT_DDP_PARTICIPANT_ID);
                    ArrayList<CohortTag> cohortTags = new ArrayList<>();
                    cohortTags.add(buildCohortTagFrom(resultSet));
                    result.merge(ddpParticipantId, cohortTags, (prev, curr) -> {
                        prev.addAll(curr);
                        return prev;
                    });
                }
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Could not fetch cohort tags for instance: " + instanceName, simpleResult.resultException);
        }
        return result;
    }

    @Override
    public List<Integer> bulkCohortCreate(List<CohortTag> cohortTags) {
        List<Integer> ids = new ArrayList<>();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_COHORT_TAG, Statement.RETURN_GENERATED_KEYS)) {
                for (CohortTag tag: cohortTags) {
                    stmt.setString(1, tag.getCohortTagName());
                    stmt.setString(2, tag.getDdpParticipantId());
                    stmt.setInt(3, tag.getDdpInstanceId());
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setString(5, tag.getCreatedBy());
                    stmt.addBatch();
                }
                stmt.executeBatch();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    while (rs.next()) {
                        ids.add(rs.getInt(1));
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException(
                    "Error inserting cohort tags of size: " + cohortTags.size(),
                    simpleResult.resultException);
        }
        logger.info(
                String.format(
                        "Cohort tags of size: %s have been inserted successfully",
                        cohortTags.size()
                ));
        return ids;
    }

    private CohortTag buildCohortTagFrom(ResultSet resultSet) throws SQLException {
        CohortTag cohortTag = new CohortTag();
        cohortTag.setCohortTagId(resultSet.getInt(COHORT_TAG_ID));
        cohortTag.setCohortTagName(resultSet.getString(COHORT_TAG_NAME));
        cohortTag.setDdpParticipantId(resultSet.getString(COHORT_DDP_PARTICIPANT_ID));
        cohortTag.setDdpInstanceId(resultSet.getInt(COHORT_DDP_INSTANCE_ID));
        return cohortTag;
    }
}
