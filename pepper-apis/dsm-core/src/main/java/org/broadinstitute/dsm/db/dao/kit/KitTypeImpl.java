package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.lddp.db.SimpleResult;

public class KitTypeImpl implements KitTypeDao {

    private static final String INSERT_KIT_TYPE = "INSERT INTO "
            + "kit_type "
            + "(kit_type_name, "
            + "bsp_material_type, "
            + "bsp_receptacle_type, "
            + "customs_json, "
            + "required_role, "
            + "manual_sent_track, "
            + "requires_insert_in_kit_tracking, "
            + "no_return) "
            + "VALUES (?,?,?,?,?,?,?,?)";

    private static final String SQL_DELETE_KIT_TYPE = "DELETE FROM "
            + "kit_type "
            + "WHERE kit_type_id = ?";

    @Override
    public int create(KitTypeDto kitTypeDto) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, kitTypeDto.getKitTypeName());
                stmt.setString(2, kitTypeDto.getBspMaterialType());
                stmt.setString(3, kitTypeDto.getBspReceptacleTyep());
                stmt.setString(4, kitTypeDto.getCustomsJson());
                stmt.setInt(5, kitTypeDto.getRequiredRole());
                stmt.setBoolean(6, kitTypeDto.getManualSentTrack());
                stmt.setBoolean(7, kitTypeDto.getRequiresInsertInKitTracking());
                stmt.setBoolean(8, kitTypeDto.getNoReturn());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new RuntimeException("Error inserting kit type with type name: " + kitTypeDto.getKitTypeName(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_KIT_TYPE)) {
                stmt.setLong(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting kit type with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<KitTypeDto> get(long id) {
        return Optional.empty();
    }
}
