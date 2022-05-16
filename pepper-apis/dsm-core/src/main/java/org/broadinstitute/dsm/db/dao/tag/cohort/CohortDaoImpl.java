package org.broadinstitute.dsm.db.dao.tag.cohort;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.tag.cohort.CohortDto;
import org.broadinstitute.lddp.db.SimpleResult;

public class CohortDaoImpl implements CohortDao {


    private static final String SQL_INSERT_COHORT_TAG = "INSERT INTO cohort_tag SET tagName = ?, participantId = ?";

    @Override
    public int create(CohortDto cohortDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_COHORT_TAG, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, cohortDto.getTagName());
                stmt.setString(2, cohortDto.getDdpParticipantId());
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
                    String.format("Error inserting cohort tag for participant with id: %s", cohortDto.getDdpParticipantId()),
                    simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<CohortDto> get(long id) {
        return Optional.empty();
    }
}
