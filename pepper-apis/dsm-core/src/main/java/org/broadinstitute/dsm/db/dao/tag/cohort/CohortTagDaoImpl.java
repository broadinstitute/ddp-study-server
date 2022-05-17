package org.broadinstitute.dsm.db.dao.tag.cohort;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CohortTagDaoImpl implements CohortTagDao {

    private static final Logger logger = LoggerFactory.getLogger(CohortTagDaoImpl.class);

    private static final String SQL_INSERT_COHORT_TAG =
            "INSERT INTO cohort_tag SET cohort_tag_name = ?, ddp_participant_id = ?, ddp_instance_id = ?";
    private static final String SQL_DELETE_COHORT_TAG_BY_ID = "DELETE FROM cohort_tag WHERE cohort_tag_id = ?";

    @Override
    public int create(CohortTag cohortTagDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_COHORT_TAG, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, cohortTagDto.getCohortTagName());
                stmt.setString(2, cohortTagDto.getDdpParticipantId());
                stmt.setInt(3, cohortTagDto.getDdpInstanceId());
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
}
