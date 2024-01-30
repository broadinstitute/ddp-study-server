package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BSPKitDao implements Dao<BSPKitDto> {

    private final String sqlUpdateKitReceived = "UPDATE ddp_kit kit INNER JOIN( SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id "
            + "FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id "
            + "AND kit.dsm_kit_id = groupedKit.kit_id SET receive_date = ?, receive_by = ? "
            + "WHERE kit.receive_date IS NULL AND kit.kit_label = ?";
    private final String SQL_SELECT_METADATA_FOR_KIT =
            "select realm.instance_name,  realm.base_url,  request.bsp_collaborator_sample_id, collection_date, "
                    + "request.bsp_collaborator_participant_id,  realm.bsp_group,  realm.bsp_collection, "
                    + "realm.bsp_organism,  realm.notification_recipients,  request.ddp_participant_id, "
                    + "kt.kit_type_name,  kt.bsp_material_type,  kt.bsp_receptacle_type, "
                    + "(select count(role.name) from ddp_instance realm2, "
                    + " ddp_instance_role inRol, instance_role role where realm2.ddp_instance_id = inRol.ddp_instance_id "
                    + "and inRol.instance_role_id = role.instance_role_id  and role.name = ? "
                    + "and realm2.ddp_instance_id = realm.ddp_instance_id) as 'has_role', "
                    + "ex.ddp_participant_exit_id,  kit.deactivated_date from "
                    + "ddp_kit_request request left join ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id "
                    + "left join ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id "
                    + "left join kit_type kt on request.kit_type_id = kt.kit_type_id "
                    + "left join ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id and "
                    + "request.ddp_instance_id = ex.ddp_instance_id) where  kit.kit_label = ?";

    Logger logger = LoggerFactory.getLogger(BSPKitDao.class);

    @Override
    public int create(BSPKitDto bspKitDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<BSPKitDto> get(long id) {
        return Optional.empty();
    }

    public Optional<BSPKitDto> getBSPKitQueryResult(@NonNull String kitLabel) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_METADATA_FOR_KIT)) {
                    stmt.setString(1, DBConstants.KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED);
                    stmt.setString(2, kitLabel);
                    try (ResultSet rs = stmt.executeQuery()) {
                        int numRows = 0;
                        while (rs.next()) {
                            numRows++;
                            dbVals.resultValue = new BSPKitDto(rs.getString(DBConstants.INSTANCE_NAME), rs.getString(DBConstants.BASE_URL),
                                    rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID),
                                    rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID), rs.getString(DBConstants.BSP_ORGANISM),
                                    rs.getString(DBConstants.BSP_COLLECTION), rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                    rs.getString(DBConstants.BSP_MATERIAL_TYPE), rs.getString(DBConstants.BSP_RECEPTABLE_TYPE),
                                    rs.getBoolean(DBConstants.HAS_ROLE), rs.getString(DBConstants.DDP_PARTICIPANT_EXIT_ID),
                                    rs.getString(DBConstants.DSM_DEACTIVATED_DATE), rs.getString(DBConstants.NOTIFICATION_RECIPIENT),
                                    rs.getString("kt." + DBConstants.KIT_TYPE_NAME), rs.getString(DBConstants.COLLECTION_DATE));
                        }
                        if (numRows > 1) {
                            throw new RuntimeException("Found " + numRows + " kits for kit label " + kitLabel);
                        }
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up kit info for kit " + kitLabel, results.resultException);
        }
        return Optional.ofNullable((BSPKitDto) results.resultValue);
    }

    public void setKitReceivedAndTriggerDDP(String kitLabel, boolean triggerDDP, BSPKitDto bspKitDto, String receiver) {
        TransactionWrapper.inTransaction(conn -> {
            boolean firstTimeReceived = false;
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateKitReceived)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, receiver);
                stmt.setString(3, kitLabel);
                int result = stmt.executeUpdate();
                if (result > 1) { // 1 row or 0 row updated is perfect
                    throw new DsmInternalError("Error updating kit w/label " + kitLabel + " (updated " + result + " rows)");
                }
                firstTimeReceived = result == 1;
            } catch (Exception e) {
                logger.error("Failed to set kit w/ label " + kitLabel + " as received ", e);
            }
            if (triggerDDP) {
                BSPKit bspKit = new BSPKit();
                // TODO: this should not be in a DB transaction since it makes a call to an external service -DC
                bspKit.triggerDDP(conn, bspKitDto, firstTimeReceived, kitLabel);
            }
            return firstTimeReceived;
        });
    }
}
