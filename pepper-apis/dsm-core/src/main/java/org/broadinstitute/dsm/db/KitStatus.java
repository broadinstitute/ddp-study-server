package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitStatus {

    private static final String SQL_SELECT_SAMPLES_STATUS = "SELECT ty.kit_type_name, kit.scan_date, kit.receive_date, kit.tracking_to_id, kit.easypost_shipment_date, cs.carrier as carrierTo FROM ddp_kit_request req LEFT JOIN ddp_kit kit on (kit.dsm_kit_request_id = req.dsm_kit_request_id) " +
            "LEFT JOIN kit_type ty on (req.kit_type_id = ty.kit_type_id) LEFT JOIN ddp_kit_request_settings ks on (ks.ddp_instance_id = req.ddp_instance_id AND ks.kit_type_id = req.kit_type_id) " +
            "LEFT JOIN carrier_service cs on (ks.carrier_service_to_id = cs.carrier_service_id) WHERE req.ddp_participant_id = ? AND req.ddp_instance_id = ? AND kit.scan_date IS NOT NULL AND kit.deactivation_reason IS NULL";

    private static final String SQL_SELECT_BATCH_SAMPLES_STATUS = "SELECT req.ddp_kit_request_id, req.ddp_participant_id , ty.kit_type_name, kit.scan_date, kit.receive_date, kit.tracking_to_id, kit.easypost_shipment_date, cs.carrier as carrierTo FROM ddp_kit_request req LEFT JOIN ddp_kit kit on (kit.dsm_kit_request_id = req.dsm_kit_request_id) " +
            "LEFT JOIN kit_type ty on (req.kit_type_id = ty.kit_type_id) LEFT JOIN ddp_kit_request_settings ks on (ks.ddp_instance_id = req.ddp_instance_id AND ks.kit_type_id = req.kit_type_id) " +
            "LEFT JOIN carrier_service cs on (ks.carrier_service_to_id = cs.carrier_service_id) WHERE req.ddp_instance_id = ? AND kit.scan_date IS NOT NULL AND kit.deactivation_reason IS NULL";


    private String kitRequestId;
    private String kitType;
    private String trackingId;
    private String carrier;
    private Long sent;
    private Long delivered;
    private Long received;

    public KitStatus(String kitType, String trackingId, String carrier, Long sent, Long delivered, Long received) {
        this.kitType = kitType;
        this.trackingId = trackingId;
        this.carrier = carrier;
        this.sent = sent;
        this.delivered = delivered;
        this.received = received;
    }

    public KitStatus(String kitRequestId, String kitType, String trackingId, String carrier, Long sent, Long delivered, Long received) {
        this.kitRequestId = kitRequestId;
        this.kitType = kitType;
        this.trackingId = trackingId;
        this.carrier = carrier;
        this.sent = sent;
        this.delivered = delivered;
        this.received = received;
    }

    public static List<KitStatus> getSampleStatus(@NonNull String ddpParticipantId, @NonNull String instanceId) {
        List<KitStatus> kitStatuses = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SAMPLES_STATUS)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Object sent = rs.getObject(DBConstants.DSM_SCAN_DATE);
                        Object delivered = rs.getObject(DBConstants.EASYPOST_SHIPMENT_DATE);
                        Object received = rs.getObject(DBConstants.DSM_RECEIVE_DATE);
                        String trackingId = rs.getString(DBConstants.DSM_TRACKING_TO);
                        kitStatuses.add(new KitStatus(rs.getString(DBConstants.KIT_TYPE_NAME),
                                trackingId,
                                trackingId != null ? rs.getString(DBConstants.DSM_CARRIER_TO) : null,
                                sent != null ? (Long) sent / 1000 : null,
                                delivered != null ? (Long) sent / 1000 : null,
                                received != null ? (Long) received / 1000 : null));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of kit status ", results.resultException);
        }
        if (!kitStatuses.isEmpty()) {
            return kitStatuses;
        }
        return null;
    }

    public static Map<String, List<KitStatus>> getBatchOfSampleStatus( @NonNull String instanceId) {
        Map<String, List<KitStatus>> results = new HashMap<>();
        SimpleResult simpleResult = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BATCH_SAMPLES_STATUS)) {
                stmt.setString(1, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        Object sent = rs.getObject(DBConstants.DSM_SCAN_DATE);
                        Object delivered = rs.getObject(DBConstants.EASYPOST_SHIPMENT_DATE);
                        Object received = rs.getObject(DBConstants.DSM_RECEIVE_DATE);
                        String trackingId = rs.getString(DBConstants.DSM_TRACKING_TO);
                        List<KitStatus> kitStatuses= results.getOrDefault(ddpParticipantId, new ArrayList<>());
                        kitStatuses.add(new KitStatus(rs.getString("ddp_kit_request_id"),
                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                trackingId,
                                trackingId != null ? rs.getString(DBConstants.DSM_CARRIER_TO) : null,
                                sent != null ? (Long) sent / 1000 : null,
                                delivered != null ? (Long) sent / 1000 : null,
                                received != null ? (Long) received / 1000 : null
                                ));
                        results.put(ddpParticipantId, kitStatuses);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error getting list of Kit Statuses for a batch ", simpleResult.resultException);
        }
        return results;
    }
}
