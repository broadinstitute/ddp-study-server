package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.LatestKitRequest;
import org.broadinstitute.dsm.model.KitRequest;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitSubKits;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.ddp.KitDetail;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.externalShipper.ExternalShipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DDPKitRequest {

    private static final Logger logger = LoggerFactory.getLogger(DDPKitRequest.class);

    public static final String UPLOADED_KIT_REQUEST = "UPLOADED_";
    public static final String MIGRATED_KIT_REQUEST = "MIGRATED_";

    private static final String SELECT_APPROVED_ORDERS = "SELECT * FROM ddp_kit_request request LEFT JOIN ddp_kit kit on kit.dsm_kit_request_id = request.dsm_kit_request_id " +
            "LEFT JOIN ddp_kit_request_settings settings on settings.ddp_instance_id = request.ddp_instance_id " +
            "LEFT JOIN ddp_instance realm on (realm.ddp_instance_id = settings.ddp_instance_id) " +
            "where request.ddp_instance_id = ? and kit.needs_approval = true and kit.authorization = true and request.external_order_status is null";

    /**
     * Requesting 'new' DDPKitRequests and write them into ddp_kit_request
     *
     * @param latestKitRequests List<LatestKitRequest>
     * @throws Exception
     */
    public void requestAndWriteKitRequests(List<LatestKitRequest> latestKitRequests) {
        logger.info("Request kits from the DDPs");
        try {
            for (LatestKitRequest latestKit : latestKitRequests) {
                try {
                    String dsmRequest = latestKit.getBaseURL() + RoutePath.DDP_KIT_REQUEST;
                    if (latestKit.getLatestDDPKitRequestID() != null) {
                        dsmRequest += "/" + latestKit.getLatestDDPKitRequestID();
                    }
                    KitDetail[] kitDetails = DDPRequestUtil.getResponseObject(KitDetail[].class, dsmRequest, latestKit.getInstanceName(), latestKit.isHasAuth0Token());
                    if (kitDetails != null) {
                        logger.info("Got " + kitDetails.length + " 'new' KitRequests from " + latestKit.getInstanceName());
                        if (kitDetails.length > 0) {

                            Map<String, KitType> kitTypes = KitType.getKitLookup();
                            Map<Integer, KitRequestSettings> kitRequestSettingsMap = KitRequestSettings.getKitRequestSettings(latestKit.getInstanceID());

                            for (KitDetail kitDetail : kitDetails) {
                                if (kitDetail != null && kitDetail.getParticipantId() != null && kitDetail.getKitRequestId() != null
                                        && kitDetail.getKitType() != null) {
                                    //ignore kits from the ddp which starts with internal upload prefix (mainly for mbc migration)
                                    if (StringUtils.isNotBlank(kitDetail.getKitRequestId()) && !kitDetail.getKitRequestId().startsWith(UPLOADED_KIT_REQUEST)
                                            && !kitDetail.getKitRequestId().startsWith(MIGRATED_KIT_REQUEST)) {
                                        String key = kitDetail.getKitType() + "_" + latestKit.getInstanceID();
                                        KitType kitType = kitTypes.get(key);
                                        if (kitType != null) {
                                            KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitTypeId());

                                            boolean kitHasSubKits = kitRequestSettings.getHasSubKits() != 0;

                                            //kit requests from study-server
                                            if (StringUtils.isNotBlank(latestKit.getParticipantIndexES())) {
                                                //without order list, that was only added for promise and currently is not used!
                                                DDPInstance ddpInstance = DDPInstance.getDDPInstance(latestKit.getInstanceName());
                                                Map<String, Map<String, Object>> participantsESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(ddpInstance, ElasticSearchUtil.BY_GUID + kitDetail.getParticipantId());
                                                if (participantsESData != null && !participantsESData.isEmpty()) {
                                                    Map<String, Object> participantESData = participantsESData.get(kitDetail.getParticipantId());
                                                    if (participantESData != null && !participantESData.isEmpty()) {
                                                        Map<String, Object> profile = (Map<String, Object>) participantESData.get("profile");
                                                        if (profile != null && !profile.isEmpty()) {
                                                            String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(latestKit.getBaseURL(), latestKit.getInstanceID(), latestKit.isMigrated(),
                                                                    latestKit.getCollaboratorIdPrefix(), (String) profile.get("guid"), (String) profile.get("hruid"), kitRequestSettings.getCollaboratorParticipantLengthOverwrite());

                                                            if (kitHasSubKits) {
                                                                List<KitSubKits> subKits = kitRequestSettings.getSubKits();
                                                                addSubKits(subKits, kitDetail, collaboratorParticipantId, kitRequestSettings, latestKit.getInstanceID(), null);
                                                            }
                                                            else {
                                                                KitRequestShipping.addKitRequests(latestKit.getInstanceID(), kitDetail, kitType.getKitTypeId(),
                                                                        kitRequestSettings, collaboratorParticipantId, null, null);
                                                            }
                                                        }
                                                        else {
                                                            logger.error("ES profile data was empty for participant with ddp_kit_request_id " + kitDetail.getKitRequestId());
                                                        }
                                                    }
                                                    else {
                                                        logger.error("Participant of ddp_kit_request_id " + kitDetail.getKitRequestId() + " not found in ES ");
                                                    }
                                                }
                                            }
                                            else {
                                                //kit requests from gen2 can be removed after all studies are migrated
                                                DDPParticipant participant = DDPParticipant.getDDPParticipant(latestKit.getBaseURL(), latestKit.getInstanceName(), kitDetail.getParticipantId(), latestKit.isHasAuth0Token());
                                                if (participant != null) {
                                                    // if the kit type has sub kits > like for promise
                                                    String collaboratorParticipantId = KitRequestShipping.getCollaboratorParticipantId(latestKit.getBaseURL(), latestKit.getInstanceID(), latestKit.isMigrated(),
                                                            latestKit.getCollaboratorIdPrefix(), participant.getParticipantId(), participant.getShortId(), kitRequestSettings.getCollaboratorParticipantLengthOverwrite());
                                                    //only testboston for now which is not gen2 so it won't matter
                                                    if (kitHasSubKits) {
                                                        List<KitSubKits> subKits = kitRequestSettings.getSubKits();
                                                        addSubKits(subKits, kitDetail, collaboratorParticipantId, kitRequestSettings, latestKit.getInstanceID(), null);
                                                    }
                                                    else {
                                                        // all other ddps
                                                        KitRequestShipping.addKitRequests(latestKit.getInstanceID(), kitDetail, kitType.getKitTypeId(),
                                                                kitRequestSettings, collaboratorParticipantId, null, null);
                                                    }
                                                }
                                                else {
                                                    throw new RuntimeException("No participant returned w/ " + kitDetail.getParticipantId() + " for " + latestKit.getInstanceName());
                                                }
                                            }
                                        }
                                        else {
                                            throw new RuntimeException("KitTypeId is not in kit_type table. KitTypeId " + kitDetail.getKitType());
                                        }
                                    }
                                }
                                else {
                                    logger.error("Important information for DDPKitRequest is missing. " +
                                                         kitDetail == null ? " DDPKitRequest is null " : " participantId " + kitDetail.getParticipantId() +
                                                         " kitRequest.getKitRequestId() " + kitDetail.getKitRequestId() +
                                                         " kitRequest.getKitType() " + kitDetail.getKitType());
                                    throw new RuntimeException("Important information for kitRequest is missing");
                                }
                            }
                        }
                    }
                    else {
                        logger.info("Didn't receive any kit requests from " + latestKit.getInstanceName());
                    }
                }
                catch (Exception ex) {
                    logger.error("Error requesting KitRequests from " + latestKit.getInstanceName(), ex);
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Error getting KitRequests ", e);
        }
    }

    public static void addOtherUnorderedKitsToList(Map<KitRequestSettings, ArrayList<KitRequest>> kitsToOrder) {
        for (KitRequestSettings kitRequestSettings : kitsToOrder.keySet()) {
            ArrayList<KitRequest> orderKit = kitsToOrder.get(kitRequestSettings);
            DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(kitRequestSettings.getDdpInstanceId());
            SimpleResult results = inTransaction((conn) -> {
                SimpleResult dbVals = new SimpleResult();
                try (PreparedStatement stmt = conn.prepareStatement(SELECT_APPROVED_ORDERS
                )) {
                    stmt.setInt(1, kitRequestSettings.getDdpInstanceId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        String participantsIndexES = ddpInstance.getParticipantIndexES();
                        Map<String, Map<String, Object>> participantsESData = null;
                        if (StringUtils.isNotBlank(participantsIndexES)) {
                            //could be filtered as well to have a smaller list
                            participantsESData = ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), participantsIndexES);
                        }
                        if (participantsESData != null && !participantsESData.isEmpty()) {
                            while (rs.next()) {
                                Map<String, Object> participantESData = participantsESData.get(rs.getString("" + DBConstants.DDP_PARTICIPANT_ID));
                                if (participantESData != null && !participantESData.isEmpty()) {
                                    DDPParticipant ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData,
                                            rs.getString(DBConstants.DDP_PARTICIPANT_ID));

                                    Map<String, Object> profile = (Map<String, Object>) participantESData.get("profile");
                                    if (profile != null && !profile.isEmpty()) {

                                        KitDetail kitDetail = new KitDetail(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                                rs.getString(DBConstants.DDP_KIT_REQUEST_ID),
                                                rs.getString(DBConstants.KIT_TYPE_ID),
                                                rs.getBoolean("needs_approval"));
                                        if (ddpParticipant != null) {
                                            orderKit.add(new KitRequest(rs.getString(DBConstants.DSM_KIT_REQUEST_ID), rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                                    null, null, rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER), ddpParticipant,
                                                    rs.getString(DBConstants.EXTERNAL_ORDER_STATUS),
                                                    rs.getString("subkits." + DBConstants.EXTERNAL_KIT_NAME),
                                                    rs.getLong(DBConstants.EXTERNAL_ORDER_DATE)));
                                            logger.info("Added unordered kit with external order number " + orderKit.get(orderKit.size() - 1).getExternalOrderNumber() + " to the order list");
                                        }

                                    }

                                }
                            }

                        }
                        kitsToOrder.put(kitRequestSettings, orderKit);
                    }
                    catch (SQLException ex) {
                        throw new RuntimeException("Can not query for unordered kits for external shipper " + ex);
                    }

                }
                catch (SQLException ex) {
                    dbVals.resultException = ex;
                }
                return dbVals;
            });
            if (results.resultException != null) {
                throw new RuntimeException("Error getting approved kits for external shipper " + results.resultException);
            }

        }
    }

    private String addSubKits(@NonNull List<KitSubKits> subKits, @NonNull KitDetail kitDetail, @NonNull String collaboratorParticipantId,
                              @NonNull KitRequestSettings kitRequestSettings, @NonNull String instanceId, String uploadReason) {
        int subCounter = 0;
        String externalOrderNumber = null;
        if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
            externalOrderNumber = generateExternalOrderNumber();
        }
        for (KitSubKits subKit : subKits) {
            for (int i = 0; i < subKit.getKitCount(); i++) {
                //kitRequestId needs to stay unique -> add `_[SUB_COUNTER]` to it
                KitRequestShipping.addKitRequests(instanceId, subKit.getKitName(), kitDetail.getParticipantId(),
                        subCounter == 0 ? kitDetail.getKitRequestId() : kitDetail.getKitRequestId() + "_" + subCounter, subKit.getKitTypeId(), kitRequestSettings,
                        collaboratorParticipantId, kitDetail.isNeedsApproval(), externalOrderNumber,  uploadReason);
                subCounter = subCounter + 1;
            }
        }

        return externalOrderNumber;
    }

    public static String generateExternalOrderNumber() {
        String externalOrderNumber =NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        while (DBUtil.existsExternalOrderNumber(externalOrderNumber)) {
            externalOrderNumber = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        }
        return externalOrderNumber;
    }

}
