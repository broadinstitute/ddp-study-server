package org.broadinstitute.dsm.util;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import com.easypost.model.TrackingDetail;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitUtil {

    private static final Logger logger = LoggerFactory.getLogger(KitUtil.class);

    private static final String SQL_SELECT_KIT_REQUESTS_ALL_INFO = "SELECT *, cs_to.carrier as carrierTo, cs_to.easypost_carrier_id as carrierToId, cs_to.carrier_account_number as carrierToAccountNumber, cs_to.service as serviceTo, " +
            "cs_return.carrier as carrierReturn, cs_return.easypost_carrier_id as carrierReturnId, cs_return.carrier_account_number as carrierReturnAccountNumber, " +
            "cs_return.service as serviceReturn FROM(SELECT kt.kit_type_name, ddp_site.instance_name, ddp_site.ddp_instance_id, ddp_site.base_url, ddp_site.auth0_token, ddp_site.billing_reference, " +
            "ddp_site.collaborator_id_prefix, ddp_site.es_participant_index ,req.bsp_collaborator_participant_id, req.bsp_collaborator_sample_id, req.ddp_participant_id, req.ddp_label, req.dsm_kit_request_id, " +
            "req.kit_type_id FROM kit_type kt, ddp_kit_request req, ddp_instance ddp_site where req.ddp_instance_id = ddp_site.ddp_instance_id AND req.kit_type_id = kt.kit_type_id) " +
            "as request LEFT JOIN (SELECT * FROM (SELECT kit.dsm_kit_request_id, kit.dsm_kit_id, kit.kit_complete, kit.label_url_to, kit.label_url_return, kit.tracking_to_id, kit.tracking_return_id, " +
            "kit.easypost_tracking_to_url, kit.easypost_tracking_return_url, kit.easypost_to_id, kit.scan_date, kit.label_date, kit.error, kit.message, kit.receive_date, kit.deactivated_date, " +
            "kit.easypost_address_id_to, kit.deactivation_reason, tracking.tracking_id, kit.kit_label, kit.express FROM ddp_kit kit INNER JOIN( SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id " +
            "FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id AND kit.dsm_kit_id = groupedKit.kit_id " +
            "LEFT JOIN ddp_kit_tracking tracking ON (kit.kit_label = tracking.kit_label))as wtf) as kit on kit.dsm_kit_request_id = request.dsm_kit_request_id " +
            "LEFT JOIN ddp_participant_exit ex on (ex.ddp_instance_id = request.ddp_instance_id AND ex.ddp_participant_id = request.ddp_participant_id) " +
            "LEFT JOIN ddp_kit_request_settings dkc on (request.ddp_instance_id = dkc.ddp_instance_id AND request.kit_type_id = dkc.kit_type_id) " +
            "LEFT JOIN kit_dimension dim on (dkc.kit_dimension_id = dim.kit_dimension_id) LEFT JOIN carrier_service cs_to on (dkc.carrier_service_to_id=cs_to.carrier_service_id) " +
            "LEFT JOIN carrier_service cs_return on (dkc.carrier_service_return_id=cs_return.carrier_service_id) LEFT JOIN kit_return_information ret on (dkc.kit_return_id=ret.kit_return_id) " +
            "LEFT JOIN kit_type t on (request.kit_type_id = t.kit_type_id) where ex.ddp_participant_exit_id is null";

    private static final String SQL_SELECT_COLLABORATOR_ID_KIT = "SELECT req.bsp_collaborator_participant_id FROM ddp_kit_request req WHERE req.ddp_participant_id = ? AND req.ddp_instance_id = ? LIMIT 1";
    private static final String SQL_SELECT_COLLABORATOR_ID_TISSUE = "SELECT tis.collaborator_sample_id FROM ddp_medical_record med " +
            "LEFT JOIN ddp_institution inst on (med.institution_id = inst.institution_id) LEFT JOIN ddp_participant as part on (part.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_onc_history_detail onc on (med.medical_record_id = onc.medical_record_id) LEFT JOIN ddp_tissue tis on (tis.onc_history_detail_id = onc.onc_history_detail_id) " +
            "WHERE NOT med.deleted <=> 1 AND part.ddp_participant_id = ? AND part.ddp_instance_id = ? AND tis.collaborator_sample_id IS NOT NULL LIMIT 1";
    private static final String SQL_UPDATE_COLLABORATOR_IDS = "UPDATE ddp_kit_request set bsp_collaborator_participant_id = ?, bsp_collaborator_sample_id = ? WHERE dsm_kit_request_id = ?";
    public static final String SQL_UPDATE_KIT_RECEIVED = "UPDATE ddp_kit kit INNER JOIN( SELECT dsm_kit_request_id, MAX(dsm_kit_id) AS kit_id " +
            "FROM ddp_kit GROUP BY dsm_kit_request_id) groupedKit ON kit.dsm_kit_request_id = groupedKit.dsm_kit_request_id " +
            "AND kit.dsm_kit_id = groupedKit.kit_id SET receive_date = ?, receive_by = ? WHERE kit.receive_date IS NULL AND kit.kit_label = ?";
    private static final String SQL_SELECT_UNSENT_EXPRESS_KITS = "SELECT inst.ddp_instance_id, inst.instance_name, kType.kit_type_name, kType.required_role, (SELECT count(realm.instance_name) as kitRequestCount " +
            "FROM ddp_kit_request request LEFT JOIN ddp_instance realm on request.ddp_instance_id = realm.ddp_instance_id " +
            "LEFT JOIN ddp_kit kit on request.dsm_kit_request_id = kit.dsm_kit_request_id LEFT JOIN kit_type kt on request.kit_type_id = kt.kit_type_id " +
            "LEFT JOIN ddp_participant_exit ex on (request.ddp_participant_id = ex.ddp_participant_id AND request.ddp_instance_id = ex.ddp_instance_id) " +
            "WHERE realm.instance_name = inst.instance_name AND request.kit_type_id = kType.kit_type_id AND ex.ddp_participant_exit_id IS NULL " +
            "AND NOT (kit.kit_complete <=> 1) AND kit.express = 1 AND kit.deactivated_date is null) as kitRequestCount, (SELECT count(role.name) " +
            "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id " +
            "AND inRol.instance_role_id = role.instance_role_id AND role.name = ? AND realm.ddp_instance_id = inst.ddp_instance_id) as 'has_role' " +
            "FROM ddp_instance inst, ddp_kit_request_settings kSetting, kit_type kType WHERE inst.ddp_instance_id = kSetting.ddp_instance_id " +
            "AND kType.kit_type_id = kSetting.kit_type_id AND inst.is_active = 1";
    private static final String AND_EASYPOST_TO_ID = " AND easypost_to_id IS NOT NULL AND easypost_shipment_status IS NULL";
    private static final String SQL_UPDATE_KIT = "UPDATE ddp_kit SET easypost_shipment_status = ?, easypost_shipment_date = ?, message = ? WHERE dsm_kit_id = ?";

    public static final String BOOKMARK_LABEL_CREATION_RUNNING = "label_creation_running";
    public static final String IGNORE_AUTO_DEACTIVATION = "ignore_auto_deactivation";
    public static final String SYSTEM_AUTOMATICALLY_DEACTIVATED = "system_automatically_deactivated";
    private static final String BSP = "BSP";
    //easypost end statuses
    public static final String EASYPOST_DELIVERED_STATUS = "delivered";
    private static final String EASYPOST_FAILURE_STATUS = "failure";
    private static final String EASYPOST_RETURN_SENDER_STATUS = "return_to_sender";
    private static final String EASYPOST_ERROR_STATUS = "error";

    /**
     * Query for not shipped express kit requests
     */
    public HashMap<String, String> getUnsentExpressKits(boolean showOnlyKitsWithNoExtraRole) {
        HashMap<String, String> notShippedKits = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_UNSENT_EXPRESS_KITS)) {
                stmt.setString(1, DBConstants.KIT_REQUEST_ACTIVATED);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        if (rs.getBoolean(DBConstants.HAS_ROLE)) {
                            if (showOnlyKitsWithNoExtraRole) {
                                if (rs.getString(DBConstants.REQUIRED_ROLE) == null) {
                                    String key = rs.getString(DBConstants.INSTANCE_NAME) + "_" + rs.getString(DBConstants.KIT_TYPE_NAME);
                                    String kitCount = rs.getString(DBConstants.KITREQUEST_COUNT);
                                    notShippedKits.put(key, kitCount);
                                }
                            }
                            else {
                                String key = rs.getString(DBConstants.INSTANCE_NAME) + "_" + rs.getString(DBConstants.KIT_TYPE_NAME);
                                String kitCount = rs.getString(DBConstants.KITREQUEST_COUNT);
                                notShippedKits.put(key, kitCount);
                            }
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking up unsent kits ", results.resultException);
        }
        logger.info("Found " + notShippedKits.size() + " ddp and type combinations unsent kits");
        return notShippedKits;
    }

    public static void createLabel(List<KitRequestCreateLabel> kitsLabelTriggered) {
        DBUtil.updateBookmark(System.currentTimeMillis(), BOOKMARK_LABEL_CREATION_RUNNING);
        DDPInstanceDto ddpInstanceDto = null;

        for (KitRequestCreateLabel kitLabelTriggered : kitsLabelTriggered) {
            EasyPostUtil easyPostUtil = new EasyPostUtil(kitLabelTriggered.getInstanceName());
            Address toAddress = null;
            try {
                if (StringUtils.isBlank(kitLabelTriggered.getAddressIdTo())) {

                    DDPInstance ddpInstance = DDPInstance.getDDPInstance(kitLabelTriggered.getInstanceName());

                    //TODO -> before we finally switch to ddpInstanceDao/ddpInstanceDto pair
                    ddpInstanceDto = new DDPInstanceDto.Builder()
                            .withInstanceName(ddpInstance.getName())
                            .withEsParticipantIndex(ddpInstance.getParticipantIndexES())
                            .build();

                    Map<String, Map<String, Object>> participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance,
                            ElasticSearchUtil.BY_GUID + kitLabelTriggered.getDdpParticipantId());
                    if (participantESData == null || participantESData.isEmpty()) {
                        participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance, ElasticSearchUtil.BY_LEGACY_ALTPID + kitLabelTriggered.getDdpParticipantId());
                    }
                    DDPParticipant ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantESData, kitLabelTriggered.getDdpParticipantId());
                    if (ddpParticipant != null) {
                        toAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitLabelTriggered.getKitRequestSettings(), null, ddpParticipant);
                        KitRequestShipping.updateRequest(kitLabelTriggered, ddpParticipant, kitLabelTriggered.getKitTyp(), kitLabelTriggered.getKitRequestSettings());
                    }
                    else {
                        KitRequestShipping.deactivateKitRequest(Long.parseLong(kitLabelTriggered.getDsmKitRequestId()), "Participant not found",
                                null,
                                SystemUtil.SYSTEM, ddpInstanceDto);
                        logger.error("Didn't find participant " + kitLabelTriggered.getDdpParticipantId());
                    }
                }
                else {
                    //uploaded pt
                    toAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitLabelTriggered.getKitRequestSettings(), kitLabelTriggered.getAddressIdTo(), null);
                    //uploaded pt is missing collaborator ids -> due to migration and upload with wrong shortId
                    if (kitLabelTriggered.getParticipantCollaboratorId() == null) {
                        if (StringUtils.isNotBlank(kitLabelTriggered.getBaseURL())) {
                            //DDP requested pt
                            DDPParticipant ddpParticipant = null;
                            if (StringUtils.isNotBlank(kitLabelTriggered.getParticipantIndexES())) {
                                Map<String, Map<String, Object>> participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(kitLabelTriggered.getInstanceName(), kitLabelTriggered.getParticipantIndexES());
                                ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, kitLabelTriggered.getDdpParticipantId());
                            }
                            else {
                                //DDP requested pt
                                ddpParticipant = DDPParticipant.getDDPParticipant(kitLabelTriggered.getBaseURL(), kitLabelTriggered.getInstanceName(),
                                        kitLabelTriggered.getDdpParticipantId(), kitLabelTriggered.isHasAuth0Token());

                            }
                            if (ddpParticipant != null) {
                                String collaboratorParticipantId = KitRequestShipping.generateBspParticipantID(kitLabelTriggered.getCollaboratorIdPrefix(),
                                        kitLabelTriggered.getKitRequestSettings().getCollaboratorParticipantLengthOverwrite(), ddpParticipant.getShortId());
                                String bspCollaboratorSampleType = kitLabelTriggered.getKitTyp().getKitTypeName();
                                if (kitLabelTriggered.getKitRequestSettings().getCollaboratorSampleTypeOverwrite() != null) {
                                    bspCollaboratorSampleType = kitLabelTriggered.getKitRequestSettings().getCollaboratorSampleTypeOverwrite();
                                }
                                if (collaboratorParticipantId == null) {
                                    logger.warn("CollaboratorParticipantId was too long " + ddpParticipant.getParticipantId());
                                }
                                else {
                                    updateCollaboratorIds(kitLabelTriggered, collaboratorParticipantId, bspCollaboratorSampleType);
                                }
                            }
                        }
                        else {
                            logger.error("Kit of pt  " + kitLabelTriggered.getDdpParticipantId() + " w/ kit id " + kitLabelTriggered.getDsmKitId() + " is missing collaborator id");
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Couldn't get address for participant " + kitLabelTriggered.getDdpParticipantId() + " in study " + kitLabelTriggered.getInstanceName() + " w/ kit id " + kitLabelTriggered.getDsmKitId(), e);
            }
            if (toAddress != null) {
                buyShipmentForKit(easyPostUtil, kitLabelTriggered.getDsmKitId(), kitLabelTriggered.getKitRequestSettings(),
                        kitLabelTriggered.getKitTyp(), toAddress.getId(), kitLabelTriggered.getBillingReference(), ddpInstanceDto);
            }
        }

        DBUtil.updateBookmark(0, BOOKMARK_LABEL_CREATION_RUNNING);
    }

    public static List<KitRequestCreateLabel> getListOfKitsLabelTriggered() {
        List<KitRequestCreateLabel> kitsLabelTriggered = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement bspStatement = conn.prepareStatement(SQL_SELECT_KIT_REQUESTS_ALL_INFO.concat(QueryExtension.KIT_LABEL_TRIGGERED))) {
                try (ResultSet rs = bspStatement.executeQuery()) {
                    while (rs.next()) {
                        kitsLabelTriggered.add(new KitRequestCreateLabel(rs.getString(DBConstants.DSM_KIT_ID),
                                rs.getString(DBConstants.DSM_KIT_REQUEST_ID), rs.getString(DBConstants.DDP_INSTANCE_ID),
                                rs.getString(DBConstants.INSTANCE_NAME), rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO), rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                rs.getString(DBConstants.COLLABORATOR_ID_PREFIX), rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
                                new KitRequestSettings(rs.getString(DBConstants.DSM_CARRIER_TO),
                                        rs.getString(DBConstants.DSM_CARRIER_TO_ID), rs.getString(DBConstants.DSM_SERVICE_TO),
                                        rs.getString(DBConstants.DSM_CARRIER_TO_ACCOUNT_NUMBER), rs.getString(DBConstants.DSM_CARRIER_RETURN),
                                        rs.getString(DBConstants.DSM_CARRIER_RETURN_ID), rs.getString(DBConstants.DSM_SERVICE_RETURN),
                                        rs.getString(DBConstants.DSM_CARRIER_RETURN_ACCOUNT_NUMBER), rs.getString(DBConstants.KIT_DIMENSIONS_LENGTH),
                                        rs.getString(DBConstants.KIT_DIMENSIONS_HEIGHT), rs.getString(DBConstants.KIT_DIMENSIONS_WIDTH),
                                        rs.getString(DBConstants.KIT_DIMENSIONS_WEIGHT), rs.getString(DBConstants.COLLABORATOR_SAMPLE_TYPE_OVERWRITE),
                                        rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_LENGTH_OVERWRITE),
                                        rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_NAME), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STREET1),
                                        rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STREET2), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_CITY),
                                        rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_ZIP), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_STATE),
                                        rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_COUNTRY), rs.getString(DBConstants.KIT_TYPE_RETURN_ADDRESS_PHONE),
                                        rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME), rs.getString(DBConstants.EXTERNAL_SHIPPER),
                                        rs.getString(DBConstants.EXTERNAL_CLIENT_ID), rs.getString(DBConstants.EXTERNAL_KIT_NAME),
                                        0, null,
                                        rs.getInt(DBConstants.DDP_INSTANCE_ID)), //label creation doesn't care if kit was part of sub kit...
                                new KitType(rs.getInt(DBConstants.KIT_TYPE_ID),
                                        rs.getInt(DBConstants.DDP_INSTANCE_ID),
                                        rs.getString(DBConstants.KIT_TYPE_NAME),
                                        rs.getString(DBConstants.KIT_TYPE_DISPLAY_NAME),
                                        rs.getString(DBConstants.EXTERNAL_SHIPPER),
                                        rs.getString(DBConstants.CUSTOMS_JSON)),
                                rs.getString(DBConstants.BILLING_REFERENCE), rs.getString(DBConstants.ES_PARTICIPANT_INDEX)
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of kit requests which are triggered for label creation ", results.resultException);
        }
        logger.info("Found " + kitsLabelTriggered.size() + " kit requests which should get a label");
        return kitsLabelTriggered;
    }

    private static void buyShipmentForKit(@NonNull EasyPostUtil easyPostUtil, @NonNull String dsmKitId, @NonNull KitRequestSettings kitRequestSettings,
                                          @NonNull KitType kitType, @NonNull String addressIdTo, String billingReference, DDPInstanceDto ddpInstanceDto) {
        String errorMessage = "";
        Shipment participantShipment = null;
        Shipment returnShipment = null;
        Address toAddress = null;
        try {
            toAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitRequestSettings, addressIdTo, null);
            participantShipment = KitRequestShipping.getShipment(easyPostUtil, billingReference, kitType, kitRequestSettings, false, toAddress);
        }
        catch (Exception e) {
            errorMessage = "To: " + e.getMessage();
        }
        try {
            Address returnAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitRequestSettings, null, null);
            returnShipment = KitRequestShipping.getShipment(easyPostUtil, billingReference, kitType, kitRequestSettings, true, returnAddress);
        }
        catch (Exception e) {
            errorMessage += "Return: " + e.getMessage();
        }
        KitRequestShipping.updateKit(dsmKitId, participantShipment, returnShipment, errorMessage, toAddress, false, ddpInstanceDto);
    }

    public static String getKitCollaboratorId(@NonNull String ddpParticipantId, @NonNull String realmId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_COLLABORATOR_ID_KIT)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, realmId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of assignees ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public static String getTissueCollaboratorId(@NonNull String ddpParticipantId, @NonNull String realmId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_COLLABORATOR_ID_TISSUE)) {
                stmt.setString(1, ddpParticipantId);
                stmt.setString(2, realmId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of assignees ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public static void updateCollaboratorIds(@NonNull KitRequestCreateLabel kitLabelTriggered, @NonNull String collaboratorParticipantId, @NonNull String bspCollaboratorSampleType) {
        SimpleResult results = inTransaction((conn) -> {
            String collaboratorSampleId = KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, kitLabelTriggered.getKitTyp().getKitTypeId());

            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_COLLABORATOR_IDS)) {
                stmt.setString(1, collaboratorParticipantId);
                stmt.setString(2, collaboratorSampleId);
                stmt.setString(3, kitLabelTriggered.getDsmKitRequestId());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating collaborator ids for kit request w/ id " + kitLabelTriggered.getDsmKitRequestId() + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating collaborator ids for kit request w/ id " + kitLabelTriggered.getDsmKitRequestId(), results.resultException);
        }
    }

    public static boolean setKitReceived(Connection conn, String kitLabel) {
        boolean resultBoolean = false;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT_RECEIVED)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, BSP);
            stmt.setString(3, kitLabel);
            int result = stmt.executeUpdate();
            if (result > 1) { // 1 row or 0 row updated is perfect
                throw new RuntimeException("Error updating kit w/label " + kitLabel + " (was updating " + result + " rows)");
            }
            if (result == 1) {
                resultBoolean = true;
            }
            else {
                resultBoolean = false;
            }
        }
        catch (Exception e) {
            logger.error("Failed to set kit w/ label " + kitLabel + " as received ", e);
        }
        return resultBoolean;
    }

    public static void getKitStatus() {
        //get list of ddps which have participant_status_endpoint
        List<DDPInstance> ddpInstanceList = DDPInstance.getDDPInstanceListWithRole(DBConstants.PARTICIPANT_STATUS_ENDPOINT);
        for (DDPInstance ddpInstance : ddpInstanceList) {
            if (ddpInstance.isHasRole()) {
                //get list of kits for given ddp
                List<KitRequestShipping> kitRequestShippingList = getKitRequestsToCheckStatus(ddpInstance.getName());
                EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());
                SimpleResult results = inTransaction((conn) -> {

                    DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                            .withInstanceName(ddpInstance.getName())
                            .withEsParticipantIndex(ddpInstance.getParticipantIndexES())
                            .build();

                    for (KitRequestShipping kitRequest : kitRequestShippingList) {
                        if (StringUtils.isNotBlank(kitRequest.getEasypostToId()) && kitRequest.getEasypostToId().startsWith("shp_")) {
                            try {
                                Shipment shipment = easyPostUtil.getShipment(kitRequest.getEasypostToId());
                                Tracker tracker = shipment.getTracker();
                                if (tracker != null) {
                                    String status = tracker.getStatus();
                                    if (StringUtils.isNotBlank(status)) {
                                        if (EASYPOST_DELIVERED_STATUS.equals(status) || EASYPOST_FAILURE_STATUS.equals(status) ||
                                                EASYPOST_ERROR_STATUS.equals(status) || EASYPOST_RETURN_SENDER_STATUS.equals(status)) {
                                            String message = null;
                                            Long deliveredDate = null;
                                            List<TrackingDetail> details = tracker.getTrackingDetails();
                                            for (int i = details.size() - 1; i >= 0; i--) {
                                                TrackingDetail detail = details.get(i);
                                                //only check for time, if kit was delivered
                                                if (EASYPOST_DELIVERED_STATUS.equals(detail.getStatus())) {
                                                    //get time of delivery
                                                    deliveredDate = detail.getDatetime().getTime();
                                                    break;
                                                }
                                                else if (status.equals(detail.getStatus())) {
                                                    //get message of the other end statuses
                                                    deliveredDate = detail.getDatetime().getTime();
                                                    message = detail.getMessage();
                                                    break;
                                                }
                                            }
                                            //write info in db
                                            String kitMessage = kitRequest.getMessage();
                                            if (StringUtils.isNotBlank(message)) {
                                                if (StringUtils.isNotBlank(kitMessage)) {
                                                    kitMessage = kitMessage.concat(" " + message);
                                                }
                                                else {
                                                    kitMessage = message;
                                                }
                                            }
                                            SimpleResult result = updateKit(conn, status, deliveredDate, kitMessage,
                                                    kitRequest.getDsmKitId(), ddpInstanceDto);
                                            if (result.resultException != null) {
                                                logger.error("Error updating kit for kit request " + kitRequest.getDsmKitRequestId() + " of realm " + ddpInstance.getName(), result.resultException);
                                            }

                                        }
                                    }
                                }
                            }
                            catch (EasyPostException epe) {
                                logger.error("Couldn't get shipment information from Easypost ", epe);
                            }

                        }
                    }
                    return null;
                });
            }
        }
        //update status of kit
    }

    private static List<KitRequestShipping> getKitRequestsToCheckStatus(@NonNull String realm) {
        List<KitRequestShipping> kits = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(KitRequestShipping.SQL_SELECT_KIT_REQUEST + QueryExtension.BY_REALM + AND_EASYPOST_TO_ID)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        KitRequestShipping kitRequest = new KitRequestShipping(
                                rs.getLong(DBConstants.DSM_KIT_REQUEST_ID),
                                rs.getLong(DBConstants.DSM_KIT_ID),
                                rs.getString(DBConstants.EASYPOST_TO_ID),
                                rs.getString(DBConstants.EASYPOST_ADDRESS_ID_TO),
                                rs.getBoolean(DBConstants.ERROR),
                                rs.getString(DBConstants.MESSAGE)
                        );
                        kits.add(kitRequest);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error getting kit requests for realm " + realm, results.resultException);
        }
        return kits;
    }

    // update kit with label trigger user and date
    private static SimpleResult updateKit(@NonNull Connection conn, String status, Long date, String message,
                                          long dsmKitId, DDPInstanceDto ddpInstanceDto) {
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_KIT)) {
            stmt.setString(1, status);
            stmt.setLong(2, date);
            stmt.setString(3, message);
            stmt.setLong(4, dsmKitId);

            int result = stmt.executeUpdate();
            if (result != 1) {
                throw new RuntimeException("Error updating kit " + dsmKitId + " it was updating " + result + " rows");
            }
        }
        catch (Exception e) {
            dbVals.resultException = e;
        }

        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setEasypostShipmentStatus(status);
        kitRequestShipping.setMessage(message);
        kitRequestShipping.setDsmKitId(dsmKitId);

        UpsertPainlessFacade.of(DBConstants.DDP_KIT_REQUEST_ALIAS, kitRequestShipping, ddpInstanceDto, ESObjectConstants.DSM_KIT_ID, ESObjectConstants.DSM_KIT_ID, dsmKitId)
                        .export();

        return dbVals;
    }

    public static List<KitRequestShipping> findSpecialBehaviorKits(@NonNull NotificationUtil notificationUtil) {
        List<DDPInstance> ddpInstances = DDPInstance.getDDPInstanceListWithKitBehavior();
        for (DDPInstance ddpInstance : ddpInstances) {
            //only instances which have special kit behavior
            if (ddpInstance.getInstanceSettings() != null && ddpInstance.getInstanceSettings().getKitBehaviorChange() != null) {
                Value uploaded = null;
                if (ddpInstance.getInstanceSettings().getKitBehaviorChange() != null) {
                    List<Value> kitBehavior = ddpInstance.getInstanceSettings().getKitBehaviorChange();
                    try {
                        uploaded = kitBehavior.stream().filter(o -> o.getName().equals(InstanceSettings.INSTANCE_SETTING_UPLOADED)).findFirst().get();
                    }
                    catch (NoSuchElementException e) {
                        uploaded = null;
                    }
                }
                // only look up kits if instance has special kit behavior for uploaded and has data in ES
                if (uploaded != null && StringUtils.isNotBlank(ddpInstance.getParticipantIndexES())) {
                    List<org.broadinstitute.dsm.db.KitType> kitTypes = org.broadinstitute.dsm.db.KitType.getKitTypes(ddpInstance.getName(), null);
                    Map<String, Map<String, Object>> participants = ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
                    if (participants != null) {
                        for (org.broadinstitute.dsm.db.KitType kitType : kitTypes) {
                            List<KitRequestShipping> kitRequestList = KitRequestShipping.getKitRequestsByRealm(ddpInstance.getName(),
                                    InstanceSettings.INSTANCE_SETTING_UPLOADED, kitType.getName());
                            for (KitRequestShipping kit : kitRequestList) {
                                //ignore if kit was reactivated with special behavior alert
                                if (!IGNORE_AUTO_DEACTIVATION.equals(kit.getMessage())) {
                                    Map<String, Object> participant = participants.get(kit.getParticipantId());
                                    if (participant != null) {
                                        logger.info("Checking pt " + kit.getParticipantId() + " for special behavior");
                                        boolean specialBehavior = InstanceSettings.shouldKitBehaveDifferently(participant, uploaded);
                                        if (specialBehavior) {
                                            KitRequestShipping.deactivateKitRequest(kit.getDsmKitRequestId(), SYSTEM_AUTOMATICALLY_DEACTIVATED + ": " + uploaded.getValue(),
                                                    DSMServer.getDDPEasypostApiKey(ddpInstance.getName()), SystemUtil.SYSTEM,
                                                    new DDPInstanceDao().getDDPInstanceByInstanceName(ddpInstance.getName()).orElseThrow());
                                            if (InstanceSettings.TYPE_NOTIFICATION.equals(uploaded.getType())) {
                                                String message = kitType.getName() + " kit for participant " + kit.getParticipantId() + " (<b>" + kit.getCollaboratorParticipantId()
                                                        + "</b>) was deactivated per background job <br>. " + uploaded.getValue();
                                                notificationUtil.sentNotification(ddpInstance.getNotificationRecipient(), message, NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE, NotificationUtil.DSM_SUBJECT);
                                            }
                                            else {
                                                logger.error("Instance settings behavior for kit was not known " + uploaded.getType());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
