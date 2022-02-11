package org.broadinstitute.dsm.model;

import lombok.NonNull;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitRequestExternal extends KitRequest {

    private static final Logger logger = LoggerFactory.getLogger(KitRequestExternal.class);

    private static final String SQL_UPDATE_KIT_EXTERNAL_SHIPPER = "UPDATE ddp_kit SET  tracking_to_id = ?, tracking_return_id = ?, kit_label = ?, kit_complete = 1, scan_date = ?, scan_by = ? " +
            "WHERE dsm_kit_request_id = (SELECT request.dsm_kit_request_id FROM ddp_kit_request request LEFT JOIN (SELECT * from (SELECT kit.dsm_kit_request_id, kit.kit_complete " +
            "FROM ddp_kit kit INNER JOIN(SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit " +
            "ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id)as wtf) as kit ON kit.dsm_kit_request_id = request.dsm_kit_request_id " +
            "WHERE request.dsm_kit_request_id = ? limit 1)";
    private static final String SQL_UPDATE_KIT_REQUEST_EXTERNAL_SHIPPER_STATUS = "UPDATE ddp_kit_request SET external_order_status = ?, external_order_date = ? WHERE dsm_kit_request_id = ? AND NOT external_order_status <=> ?";
    private static final String SQL_UPDATE_KIT_REQUEST_EXTERNAL_SHIPPER_RESPONSE = "UPDATE ddp_kit_request SET external_response = ? WHERE dsm_kit_request_id = ?";

    public KitRequestExternal(long dsmKitRequestId, String participantId, String shortId, String shippingId, String externalOrderNumber,
                              DDPParticipant participant,
                              String externalOrderStatus, String externalKitName) {
        super(dsmKitRequestId, participantId, shortId, shippingId, externalOrderNumber, participant, externalOrderStatus, externalKitName, null);
    }

    // update kit request with status and date of external shipper
    public static void updateKitRequest(Connection conn, String externalOrderStatus, long externalOrderDate, String dsmKitRequestId,
                                        int instanceId) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_REQUEST_EXTERNAL_SHIPPER_STATUS)) {
            stmt.setString(1, externalOrderStatus);
            stmt.setLong(2, externalOrderDate);
            stmt.setString(3, dsmKitRequestId);
            stmt.setString(4, externalOrderStatus);

            int result = stmt.executeUpdate();
            if (result > 1) {
                throw new RuntimeException("Error updating kit request w/ dsm_kit_request_id " + dsmKitRequestId + " it was updating " + result + " rows");
            }
        }
        catch (Exception e) {
           throw new RuntimeException("Error updating kit request w/ dsm_kit_request_id " + dsmKitRequestId, e);
        }
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setDsmKitRequestId(Long.valueOf(dsmKitRequestId));
        kitRequestShipping.setExternalOrderStatus(externalOrderStatus);
        kitRequestShipping.setExternalOrderDate(externalOrderDate);
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceId(instanceId).orElseThrow();
        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_REQUEST_ID,
                ESObjectConstants.DSM_KIT_REQUEST_ID, dsmKitRequestId).export();
    }

    // update kit request with response of external shipper
    public static void updateKitRequestResponse(@NonNull Connection conn, String externalResponse, String dsmKitRequestId) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_REQUEST_EXTERNAL_SHIPPER_RESPONSE)) {
            stmt.setString(1, externalResponse);
            stmt.setString(2, dsmKitRequestId);

            int result = stmt.executeUpdate();
            if (result > 1) {
                throw new RuntimeException("Error updating kit request w/ dsm_kit_request_id " + dsmKitRequestId + " it was updating " + result + " rows");
            }
        }
        catch (Exception e) {
            logger.error("Error updating kit request w/ dsm_kit_request_id " + dsmKitRequestId, e);
        }
    }

    // update kit request with response of external shipper
    public static void updateKitRequestResponse(@NonNull Connection conn, String trackingIdTo, String trackingIdReturn, String kitLabel, long sentDate,
                                                String sentBy, String dsmKitRequestId, int ddpInstanceId) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_EXTERNAL_SHIPPER)) {
            stmt.setString(1, trackingIdTo);
            stmt.setString(2, trackingIdReturn);
            stmt.setString(3, kitLabel);
            stmt.setLong(4, sentDate);
            stmt.setString(5, sentBy);
            stmt.setString(6, dsmKitRequestId);

            int result = stmt.executeUpdate();
            if (result != 1) {
                throw new RuntimeException("Error updating kit w/ dsm_kit_request_id " + dsmKitRequestId + " it was updating " + result + " rows");
            }
        }
        catch (Exception e) {
            logger.error("Error updating kit w/ dsm_kit_request_id " + dsmKitRequestId, e);
        }
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setDsmKitRequestId(Long.valueOf(dsmKitRequestId));
        kitRequestShipping.setTrackingToId(trackingIdTo);
        kitRequestShipping.setTrackingReturnId(trackingIdReturn);
        kitRequestShipping.setKitLabel(kitLabel);
        kitRequestShipping.setScanDate(sentDate);

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceId(ddpInstanceId).orElseThrow();

        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_REQUEST_ID, ESObjectConstants.DSM_KIT_REQUEST_ID, dsmKitRequestId)
                .export();

    }
}
