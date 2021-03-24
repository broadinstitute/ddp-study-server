package org.broadinstitute.dsm.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.DeliveryAddress;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.mbc.MBCParticipant;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class ParticipantWrapper {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);

    public static final String BY_DDP_PARTICIPANT_ID_IN = " AND request.ddp_participant_id IN (\"";
    public static final String ORDER_AND_LIMIT = " ORDER BY request.dsm_kit_request_id desc LIMIT 5000";

    private Map<String, Object> data;
    private Participant participant;
    private List<MedicalRecord> medicalRecords;
    private List<OncHistoryDetail> oncHistoryDetails;
    private List<KitRequestShipping> kits;
    private List<AbstractionActivity> abstractionActivities;
    private List<AbstractionGroup> abstractionSummary;
    private List<Map<String, Object>> proxyData;

    public ParticipantWrapper(Map<String, Object> data, Participant participant, List<MedicalRecord> medicalRecords,
                              List<OncHistoryDetail> oncHistoryDetails, List<KitRequestShipping> kits, List<AbstractionActivity> abstractionActivities,
                              List<AbstractionGroup> abstractionSummary, List<Map<String, Object>> proxyData) {
        this.data = data;
        this.participant = participant;
        this.medicalRecords = medicalRecords;
        this.oncHistoryDetails = oncHistoryDetails;
        this.kits = kits;
        this.abstractionActivities = abstractionActivities;
        this.abstractionSummary = abstractionSummary;
        this.proxyData = proxyData;
    }

    public JsonObject getDataAsJson() {
        return new JsonParser().parse(new Gson().toJson(data)).getAsJsonObject();
    }

    public static List<ParticipantWrapper> getFilteredList(@NonNull DDPInstance instance, Map<String, String> filters) {
        logger.info("Getting list of participant information");
        if (filters == null) {
            Map<String, Map<String, Object>> participantESData = getESData(instance);
            Map<String, Participant> participants = Participant.getParticipants(instance.getName());
            Map<String, List<MedicalRecord>> medicalRecords = null;
            Map<String, List<OncHistoryDetail>> oncHistoryDetails = null;
            Map<String, List<KitRequestShipping>> kitRequests = null;

            if (instance.isHasRole()) { //only needed if study has mr&tissue tracking
                medicalRecords = MedicalRecord.getMedicalRecords(instance.getName());
                oncHistoryDetails = OncHistoryDetail.getOncHistoryDetails(instance.getName());
            }
            if (DDPInstance.getRole(instance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
                kitRequests = KitRequestShipping.getKitRequests(instance, ORDER_AND_LIMIT);
            }
            Map<String, List<AbstractionActivity>> abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(instance.getName());
            Map<String, List<AbstractionGroup>> abstractionSummary = AbstractionFinal.getAbstractionFinal(instance.getName());
            Map<String, Map<String, Object>> proxyData = getProxyData(instance);

            //baselist should be not gen2/mbc db
            List<String> baseList = null;
            if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                baseList = new ArrayList<>(participantESData.keySet());
            }
            else {
                baseList = new ArrayList<>(participants.keySet());
            }

            List<ParticipantWrapper> r = addAllData(baseList, participantESData, participants, medicalRecords, oncHistoryDetails, kitRequests, abstractionActivities, abstractionSummary, proxyData);
            return r;
        }
        else {
            //no filters, return all participants which came from ES with DSM data added to it
            Map<String, Map<String, Object>> participantESData = null;
            Map<String, Participant> participants = null;
            Map<String, List<MedicalRecord>> medicalRecords = null;
            Map<String, List<OncHistoryDetail>> oncHistories = null;
            Map<String, List<KitRequestShipping>> kitRequests = null;
            Map<String, List<AbstractionActivity>> abstractionActivities = null;
            Map<String, List<AbstractionGroup>> abstractionSummary = null;
            Map<String, Map<String, Object>> proxyData = null;
            List<String> baseList = null;
            //filter the lists depending on filter
            for (String source : filters.keySet()) {
                if (StringUtils.isNotBlank(filters.get(source))) {
                    if (DBConstants.DDP_PARTICIPANT_ALIAS.equals(source)) {
                        participants = Participant.getParticipants(instance.getName(), filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(participants.keySet()));
                    }
                    else if (DBConstants.DDP_MEDICAL_RECORD_ALIAS.equals(source)) {
                        medicalRecords = MedicalRecord.getMedicalRecords(instance.getName(), filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(medicalRecords.keySet()));
                    }
                    else if (DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(source)) {
                        oncHistories = OncHistoryDetail.getOncHistoryDetails(instance.getName(), filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(oncHistories.keySet()));
                    }
                    else if (DBConstants.DDP_KIT_REQUEST_ALIAS.equals(source)) {
                        kitRequests = KitRequestShipping.getKitRequests(instance, filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(kitRequests.keySet()));
                    }
                    else if (DBConstants.DDP_ABSTRACTION_ALIAS.equals(source)) {
                        abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(instance.getName(), filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(abstractionActivities.keySet()));
                    }
                    //                else if (DBConstants.DDP_ABSTRACTION_ALIAS.equals(source)) {
                    //                    abstractionSummary = AbstractionFinal.getAbstractionFinal(instance.getName(), filters.get(source));
                    //                    baseList = getCommonEntries(baseList, new ArrayList<>(abstractionSummary.keySet()));
                    //                }
                    else if (StringUtils.isNotBlank(instance.getParticipantIndexES())){
                        participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(participantESData.keySet()));
                    }
                }
            }
            //get all the list which were not filtered
            if (participantESData == null) {
                //get only pts for the filtered kitRequests
                if (baseList!= null && !baseList.isEmpty()){
                    if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                        String filter = Arrays.stream(baseList.toArray(new String[0])).collect(Collectors.joining(ElasticSearchUtil.BY_GUIDS));
                        participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, ElasticSearchUtil.BY_GUID + filter);
                        if (instance.isMigratedDDP()) {//also check for legacyAltPid
                            String filterLegacy = Arrays.stream(baseList.toArray(new String[0])).collect(Collectors.joining(ElasticSearchUtil.BY_LEGACY_ALTPIDS));
                            Map<String, Map<String, Object>> tmp = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, ElasticSearchUtil.BY_LEGACY_ALTPID + filterLegacy);
                            participantESData = Stream.of(participantESData, tmp).flatMap(m -> m.entrySet().stream())
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        }
                    }
                    else {
                        Map<String, ParticipantExit> exitedParticipants = ParticipantExit.getExitedParticipants(instance.getName(), false);
                        participantESData = parseGen2toESParticipant(DDPRequestUtil.getDDPParticipant(instance), instance.getName(), exitedParticipants);
                    }
                }
                else {
                    //get all pts
                    participantESData = getESData(instance);
                }
            }
            if (participants == null) {
                participants = Participant.getParticipants(instance.getName());
            }
            if (medicalRecords == null && instance.isHasRole()) {
                medicalRecords = MedicalRecord.getMedicalRecords(instance.getName());
            }
            if (oncHistories == null && instance.isHasRole()) {
                oncHistories = OncHistoryDetail.getOncHistoryDetails(instance.getName());
            }
            if (kitRequests == null && DDPInstance.getRole(instance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
                //get only kitRequests for the filtered pts
                if (participantESData != null && !participantESData.isEmpty()) {
                    String filter = Arrays.stream(participantESData.keySet().toArray(new String[0])).collect(Collectors.joining("\",\""));
                    logger.info("About to query for kits from " + participantESData.size() + " participants");
                    kitRequests = KitRequestShipping.getKitRequests(instance, BY_DDP_PARTICIPANT_ID_IN + filter + "\")");
                }
                else {
                    //get all kitRequests
                    kitRequests = KitRequestShipping.getKitRequests(instance, ORDER_AND_LIMIT);
                }
            }
            if (abstractionActivities == null) {
                abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(instance.getName());
            }
            if (abstractionSummary == null) {
                abstractionSummary = AbstractionFinal.getAbstractionFinal(instance.getName());
            }
            if (proxyData == null) {
                proxyData = getProxyData(instance);
            }
            //baselist should be not gen2/mbc db
            if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                baseList = getCommonEntries(baseList, new ArrayList<>(participantESData.keySet()));
            }
            else {
                baseList = getCommonEntries(baseList, new ArrayList<>(participants.keySet()));
            }
            //bring together all the information

            List<ParticipantWrapper> r = addAllData(baseList, participantESData, participants, medicalRecords, oncHistories, kitRequests, abstractionActivities, abstractionSummary, proxyData);
            return r;
        }
    }

    public static Map<String, Map<String, Object>> getESData(@NonNull DDPInstance instance) {
        if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
            return ElasticSearchUtil.getDDPParticipantsFromES(instance.getName(), instance.getParticipantIndexES());
        }
        else {
            Map<String, ParticipantExit> exitedParticipants = ParticipantExit.getExitedParticipants(instance.getName(), false);
            return parseGen2toESParticipant(DDPRequestUtil.getDDPParticipant(instance), instance.getName(), exitedParticipants);
        }
    }

    public static Map<String, Map<String, Object>> getProxyData(@NonNull DDPInstance instance) {
        if (StringUtils.isNotBlank(instance.getUsersIndexES())) {
            return ElasticSearchUtil.getDDPParticipantsFromES(instance.getName(), instance.getUsersIndexES());
        }
        return null;
    }

    private static List<String> getCommonEntries(List<String> list1, List<String> list2) {
        if (list1 == null) {
            return list2;
        }
        else {
            list1.retainAll(list2);
            return list1;
        }
    }

    public static List<ParticipantWrapper> addAllData(List<String> baseList, Map<String, Map<String, Object>> esDataMap,
                                                      Map<String, Participant> participantMap, Map<String, List<MedicalRecord>> medicalRecordMap,
                                                      Map<String, List<OncHistoryDetail>> oncHistoryMap, Map<String, List<KitRequestShipping>> kitRequestMap,
                                                      Map<String, List<AbstractionActivity>> abstractionActivityMap, Map<String, List<AbstractionGroup>> abstractionSummary,
                                                      Map<String, Map<String, Object>> proxyData) {
        List<ParticipantWrapper> participantList = new ArrayList<>();
        for (String ddpParticipantId : baseList) {
            Participant participant = participantMap != null ? participantMap.get(ddpParticipantId) : null;
            Map<String, Object> participantESData = esDataMap.get(ddpParticipantId);
            if (participantESData != null) {
                participantList.add(new ParticipantWrapper(participantESData, participant,
                        medicalRecordMap != null ? medicalRecordMap.get(ddpParticipantId) : null,
                        oncHistoryMap != null ? oncHistoryMap.get(ddpParticipantId) : null,
                        kitRequestMap != null ? kitRequestMap.get(ddpParticipantId) : null,
                        abstractionActivityMap != null ? abstractionActivityMap.get(ddpParticipantId) : null,
                        abstractionSummary != null ? abstractionSummary.get(ddpParticipantId) : null,
                        getProxyProfiles(participantESData, proxyData)));
            }
        }
        logger.info("Returning list w/ " + participantList.size() + " pts now");
        return participantList;
    }

    private static List<Map<String, Object>> getProxyProfiles(Map<String, Object> participantData, Map<String, Map<String, Object>> proxyDataES) {
        if (participantData != null && !participantData.isEmpty() && proxyDataES != null && !proxyDataES.isEmpty()) {
            List<String> proxies = (List<String>) participantData.get("proxies");
            List<Map<String, Object>> proxyData = new ArrayList<>();
            if (proxies != null && !proxies.isEmpty()) {
                proxies.forEach(proxy -> {
                    Map<String, Object> proxyD = proxyDataES.get(proxy);
                    if (proxyD != null) {
                        proxyData.add(proxyD);
                    }
                });
                return proxyData;
            }
        }
        return null;
    }

    private static Map<String, Map<String, Object>> parseGen1toESParticipant(Map<String, MBCParticipant> mbcParticipants, Map<String, ParticipantExit> exitedParticipants) {
        Map<String, Map<String, Object>> participantsData = new HashMap<>();
        if (!mbcParticipants.isEmpty()) {
            for (String ddpParticipantId : mbcParticipants.keySet()) {
                ParticipantExit participantExit = null;
                if (exitedParticipants != null && !exitedParticipants.isEmpty()) {
                    participantExit = exitedParticipants.get(ddpParticipantId);
                }
                participantsData.put(ddpParticipantId, parseGen1toESParticipant(ddpParticipantId, mbcParticipants.get(ddpParticipantId), participantExit));
            }
        }
        return participantsData;
    }

    private static Map<String, Object> parseGen1toESParticipant(String ddpParticipantId, MBCParticipant mbcParticipant, ParticipantExit participantExit) {
        Map<String, Object> participantDataMap = new HashMap<>();
        Map<String, Object> participantProfileDataMap = new HashMap<>();
        participantProfileDataMap.put(ElasticSearchUtil.GUID, ddpParticipantId);
        participantProfileDataMap.put(ElasticSearchUtil.HRUID, ddpParticipantId);
        participantProfileDataMap.put("firstName", mbcParticipant.getFirstName());
        participantProfileDataMap.put("lastName", mbcParticipant.getLastName());
        participantDataMap.put(ElasticSearchUtil.PROFILE, participantProfileDataMap);
        participantDataMap.put("status", "ENROLLED");
        if (participantExit != null) {
            participantDataMap.put("status", "EXITED_AFTER_ENROLLMENT");
        }
        participantDataMap.put("ddp", "MBC");
        Map<String, Object> participantAddressMap = new HashMap<>();
        participantAddressMap.put("country", mbcParticipant.getCountry());
        participantDataMap.put(ElasticSearchUtil.ADDRESS, participantAddressMap);
        return participantDataMap;
    }

    private static Map<String, Map<String, Object>> parseGen2toESParticipant(Map<String, DDPParticipant> gen2Participants, String realm, Map<String, ParticipantExit> exitedParticipants) {
        Map<String, Map<String, Object>> participantsData = new HashMap<>();
        if (!gen2Participants.isEmpty()) {
            for (String ddpParticipantId : gen2Participants.keySet()) {
                ParticipantExit participantExit = null;
                if (exitedParticipants != null && !exitedParticipants.isEmpty()) {
                    participantExit = exitedParticipants.get(ddpParticipantId);
                }
                participantsData.put(ddpParticipantId, parseGen2toESParticipant(ddpParticipantId, gen2Participants.get(ddpParticipantId), realm, participantExit));
            }
        }
        return participantsData;
    }

    private static Map<String, Object> parseGen2toESParticipant(String ddpParticipantId, DDPParticipant ddpParticipant, String realm, ParticipantExit participantExit) {
        Map<String, Object> participantDataMap = new HashMap<>();
        Map<String, Object> participantProfileDataMap = new HashMap<>();
        participantProfileDataMap.put(ElasticSearchUtil.GUID, ddpParticipantId);
        participantProfileDataMap.put(ElasticSearchUtil.HRUID, ddpParticipant.getShortId());
        participantProfileDataMap.put("firstName", ddpParticipant.getFirstName());
        participantProfileDataMap.put("lastName", ddpParticipant.getLastName());
        participantDataMap.put(ElasticSearchUtil.PROFILE, participantProfileDataMap);
        participantDataMap.put("status", "ENROLLED");
        if (participantExit != null) {
            participantDataMap.put("status", "EXITED_AFTER_ENROLLMENT");
        }
        participantDataMap.put("ddp", realm);
        Map<String, Object> participantAddressMap = new HashMap<>();
        DeliveryAddress address = ddpParticipant.getAddress();
        if (address != null && StringUtils.isNotBlank(address.getCountry())) {
            participantAddressMap.put("country", address.getCountry());
        }
        participantDataMap.put(ElasticSearchUtil.ADDRESS, participantAddressMap);
        return participantDataMap;
    }
}
