package org.broadinstitute.dsm.db.dao.ddp.institution;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;

import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dao.util.ResultsBuilder;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DDPInstitutionDao implements Dao<DDPInstitutionDto> {

    private static final Logger logger = LoggerFactory.getLogger(DDPInstitutionDao.class);

    public static final String SQL_INSERT_NEW_DDP_INSTITUTION = "INSERT INTO ddp_institution "
            + "(ddp_institution_id, type, participant_id, last_changed) VALUES (?, ?, ?, ?)";

    public static final String SQL_SELECT_INSTITUTION_BY_INSTITUTION_ID = "SELECT * FROM ddp_institution WHERE institution_id = ?;";

    private static final String SQL_DELETE_BY_ID = "DELETE FROM ddp_institution WHERE institution_id = ?";

    public static DDPInstitutionDao of() {
        return new DDPInstitutionDao();
    }

    @Override
    public int create(DDPInstitutionDto ddpInstitutionDto) {
        logger.info(String.format("Attempting to create a new institution with participant_id = %s", ddpInstitutionDto.getParticipantId()));
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
            throw new RuntimeException("Error inserting participant with id: "
                    + ddpInstitutionDto.getParticipantId(), simpleResult.resultException);
        }
        logger.info(String.format("A new institution with participant_id = %s has been created successfully",
                ddpInstitutionDto.getParticipantId()));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_BY_ID);
        if (simpleResult.resultException != null) {
            throw new DsmInternalError("Error deleting ddp_institution record with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<DDPInstitutionDto> get(long institutionId) {
        BuildInstitution builder = new BuildInstitution();
        SimpleResult res = DaoUtil.getById(institutionId, SQL_SELECT_INSTITUTION_BY_INSTITUTION_ID, builder);
        if (res.resultException != null) {
            throw new RuntimeException("Error fetching institution with institution id: " + institutionId,
                    res.resultException);
        }
        logger.info("Got institution from DB with institution_id = {}", institutionId);
        return Optional.of((DDPInstitutionDto) res.resultValue);
    }


    private static class BuildInstitution implements ResultsBuilder {
        public Object build(ResultSet rs) throws SQLException {
            return new DDPInstitutionDto.Builder()
                    .withInstitutionId(rs.getInt(DBConstants.INSTITUTION_ID))
                    .withDdpInstitutionId(rs.getString(DBConstants.DDP_INSTITUTION_ID))
                    .withType(rs.getString(DBConstants.TYPE))
                    .withParticipantId(rs.getInt(DBConstants.PARTICIPANT_ID))
                    .withLastChanged(rs.getLong(DBConstants.LAST_CHANGED))
                    .build();
        }
    }
}
