package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
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
            + "no_return, "
            + "sample_type) "
            + "VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String SQL_DELETE_KIT_TYPE = "DELETE FROM "
            + "kit_type "
            + "WHERE kit_type_id = ?";

    private static final String SQL_SELECT_KIT_TYPE = "SELECT * FROM ddp_kit_request_settings kitSettings "
            + "LEFT JOIN kit_type type ON (type.kit_type_id = kitSettings.kit_type_id) "
            + "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = kitSettings.ddp_instance_id) "
            + " WHERE realm.instance_name = ? AND type.kit_type_name = ?";

    /**
     * Getting kitTypes based on kit type name and realm
     */
    public static KitTypeDto getKitTypes(@NonNull String realm, @NonNull String kitTypeName) {
        log.info("Finding kit typ for realm {} with kit type name {}", realm, kitTypeName);
        List<KitTypeDto> kitTypes = new ArrayList<>();
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_KIT_TYPE)) {
                stmt.setString(1, realm);
                stmt.setString(2, kitTypeName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        kitTypes.add(KitTypeDto.builder()
                                .withKitTypeId(rs.getInt(DBConstants.KIT_TYPE_ID))
                                .withKitTypeName(rs.getString(DBConstants.KIT_TYPE_NAME))
                                .withDisplayName(rs.getString(DBConstants.DISPLAY_NAME))
                                .build());
                        if (kitTypes.size() > 1) {
                            // we already found a kitType with that name for this realm, if there is more this is a config error
                            throw new DsmInternalError(
                                    String.format("More than one kit type were found for realm %s with kit type name %s", realm, kitTypeName));
                        }
                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError(ex);
            }
            return null;
        });
        if (kitTypes.isEmpty()) {
            throw new DsmInternalError(
                    String.format("No kit type was found for study %s with kit type name %s", realm, kitTypeName));
        }
        return kitTypes.get(0);
    }

    @Override
    public int create(KitTypeDto kitTypeDto) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, kitTypeDto.getKitTypeName());
                stmt.setString(2, kitTypeDto.getBspMaterialType());
                stmt.setString(3, kitTypeDto.getBspReceptacleType());
                stmt.setString(4, kitTypeDto.getCustomsJson());
                stmt.setInt(5, kitTypeDto.getRequiredRole());
                stmt.setBoolean(6, kitTypeDto.getManualSentTrack());
                stmt.setBoolean(7, kitTypeDto.getRequiresInsertInKitTracking());
                stmt.setBoolean(8, kitTypeDto.getNoReturn());
                stmt.setString(9, kitTypeDto.getSampleType());
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
