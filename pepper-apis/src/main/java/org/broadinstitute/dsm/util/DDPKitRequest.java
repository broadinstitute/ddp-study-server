package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
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
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.externalShipper.ExternalShipper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DDPKitRequest {

    private static final Logger logger = LoggerFactory.getLogger(DDPKitRequest.class);

    public static final String UPLOADED_KIT_REQUEST = "UPLOADED_";
    public static final String MIGRATED_KIT_REQUEST = "MIGRATED_";

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

                            Map<KitRequestSettings, ArrayList<KitRequest>> kitsToOrder = new HashMap<>();
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

                                            ArrayList<KitRequest> orderKit = kitsToOrder.get(kitRequestSettings);
                                            if (orderKit == null) {
                                                orderKit = new ArrayList<>();
                                            }

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
                                                                String externalOrderNumber = addSubKits(subKits, kitDetail, collaboratorParticipantId, kitRequestSettings, latestKit.getInstanceID(), null);
                                                                DDPParticipant ddpParticipant = ElasticSearchUtil.getParticipantAsDDPParticipant(participantsESData, kitDetail.getParticipantId());
                                                                if (ddpParticipant != null) {
                                                                    if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper())) {
                                                                        orderKit.add(new KitRequest(kitDetail.getParticipantId(), (String) profile.get("hruid"), ddpParticipant, externalOrderNumber));
                                                                        logger.info("Added kit with external order number " + orderKit.get(orderKit.size() - 1).getExternalOrderNumber() + " to the order list");
                                                                    }
                                                                }
                                                            }
                                                            else {
                                                                KitRequestShipping.addKitRequests(latestKit.getInstanceID(), kitDetail, kitType.getKitTypeId(),
                                                                        kitRequestSettings, collaboratorParticipantId, null, null);
                                                            }
                                                            if (StringUtils.isNotBlank(kitRequestSettings.getExternalShipper()) && !kitDetail.isNeedsApproval()) {//testboston
                                                                kitsToOrder.put(kitRequestSettings, orderKit);
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
                                                throw new RuntimeException("No participant_index was setup for  " + latestKit.getInstanceName());
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
                            //TODO PEGAH GET UNORDERED OR NOT FOUND ORDERS AND ADD THEM  TO THE LIST
                            //                            addOtherUnorderedKitsToList(kitsToOrder);

                            //only order if kit were added to kits to order hash (which should only be if a kit has an external shipper)
                            if (!kitsToOrder.isEmpty()) {
                                Iterator<KitRequestSettings> iter = kitsToOrder.keySet().iterator();
                                while (iter.hasNext()) {
                                    KitRequestSettings setting = iter.next();
                                    ArrayList<KitRequest> kits = kitsToOrder.get(setting);
                                    try {
                                        logger.info("placing order with external shipper");
                                        ExternalShipper shipper = (ExternalShipper) Class.forName(DSMServer.getClassName(setting.getExternalShipper())).newInstance();
                                        shipper.orderKitRequests(kits, new EasyPostUtil(latestKit.getInstanceName()), setting, null);
                                    }
                                    catch (Exception e) {
                                        logger.error("Failed to sent external shipper kit request order to " + setting.getExternalShipper(), e);
                                    }
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
