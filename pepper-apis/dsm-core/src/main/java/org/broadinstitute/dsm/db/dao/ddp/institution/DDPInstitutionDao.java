package org.broadinstitute.dsm.db.dao.ddp.institution;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.lddp.db.SimpleResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.*;

public class DDPInstitutionDao implements Dao<DDPInstitutionDto> {


    public static final String SQL_INSERT_NEW_DDP_INSTITUTION = "INSERT INTO ddp_institution SET ddp_institution_id = ?, type = ?, participant_id = ?, last_changed = ?";

    public static final String SQL_SELECT_INSTITUTION_BY_INSTITUTION_ID = "SELECT * FROM ddp_institution WHERE institution_id = ?;";

    private static DDPInstitutionDao ddpInstitutionDao;

    // for testing purposes
    protected DDPInstitutionDao() {

    }

    public static DDPInstitutionDao of() {
        if (ddpInstitutionDao == null) {
            ddpInstitutionDao = new DDPInstitutionDao();
        }
        return ddpInstitutionDao;
    }

    @Override
    public int create(DDPInstitutionDto ddpInstitutionDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_NEW_DDP_INSTITUTION, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, ddpInstitutionDto.getDdpInstitutionId());
                stmt.setString(2, ddpInstitutionDto.getType());
                stmt.setInt(3, ddpInstitutionDto.getParticipantId());
                stmt.setLong(4, ddpInstitutionDto.getLastChanged());
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
            throw new RuntimeException("Error inserting participant with id: " + ddpInstitutionDto.getParticipantId(), simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<DDPInstitutionDto> get(long institutionId) {
        SimpleResult result = new SimpleResult();
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTITUTION_BY_INSTITUTION_ID)) {
                stmt.setLong(1, institutionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result.resultValue = buildInstitutionFromResultSet(rs);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error while fetching institutions with institution id: " + institutionId, simpleResult.resultException);
        }
        return Optional.of((DDPInstitutionDto) result.resultValue);
    }


    private DDPInstitutionDto buildInstitutionFromResultSet(ResultSet rs) throws SQLException {
        return new DDPInstitutionDto.Builder()
                .withInstitutionId(rs.getInt(INSTITUTION_ID))
                .withDdpInstitutionId(rs.getString(DDP_INSTITUTION_ID))
                .withType(rs.getString(TYPE))
                .withParticipantId(rs.getInt(PARTICIPANT_ID))
                .withLastChanged(rs.getLong(LAST_CHANGED))
                .build();
    }
}
