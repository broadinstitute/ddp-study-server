package org.broadinstitute.dsm.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
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
    private List<ParticipantData> participantData;

    public ParticipantWrapper(Map<String, Object> data, Participant participant, List<MedicalRecord> medicalRecords,
                              List<OncHistoryDetail> oncHistoryDetails, List<KitRequestShipping> kits, List<AbstractionActivity> abstractionActivities,
                              List<AbstractionGroup> abstractionSummary, List<Map<String, Object>> proxyData, List<ParticipantData> participantData) {
        this.data = data;
        this.participant = participant;
        this.medicalRecords = medicalRecords;
        this.oncHistoryDetails = oncHistoryDetails;
        this.kits = kits;
        this.abstractionActivities = abstractionActivities;
        this.abstractionSummary = abstractionSummary;
        this.proxyData = proxyData;
        this.participantData = participantData;
    }

    public ParticipantWrapper() {}
    
    //useful to get participant from ES if short id is either hruid or legacy short id
    public static Optional<ParticipantWrapper> getParticipantByShortId(DDPInstance ddpInstance, String participantShortId) {
        Optional<ParticipantWrapper> maybeParticipant;

        if (ParticipantUtil.isHruid(participantShortId)) {
            maybeParticipant = getParticipantFromESByHruid(ddpInstance, participantShortId);
        } else {
            maybeParticipant = getParticipantFromESByLegacyShortId(ddpInstance, participantShortId);
        }
        return maybeParticipant;
    }

    public JsonObject getDataAsJson() {
        return new JsonParser().parse(new Gson().toJson(data)).getAsJsonObject();
    }

    public static List<ParticipantWrapper> getFilteredList(@NonNull DDPInstance instance, Map<String, String> filters) {
        logger.info("Getting list of participant information");

        if (StringUtils.isBlank(instance.getParticipantIndexES())) {
            throw new RuntimeException("No participant index setup in ddp_instance table for " + instance.getName());
        }
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
            if (DDPInstanceDao.getRole(instance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
                kitRequests = KitRequestShipping.getKitRequests(instance, ORDER_AND_LIMIT);
            }
            Map<String, List<AbstractionActivity>> abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(instance.getName());
            Map<String, List<AbstractionGroup>> abstractionSummary = AbstractionFinal.getAbstractionFinal(instance.getName());
            Map<String, Map<String, Object>> proxyData = getProxyData(instance);
            Map<String, List<ParticipantData>> participantData = ParticipantData.getParticipantData(instance.getName());

            List<String> baseList = new ArrayList<>(participantESData.keySet());
            List<ParticipantWrapper> r = addAllData(baseList, participantESData, participants, medicalRecords, oncHistoryDetails, kitRequests, abstractionActivities, abstractionSummary, proxyData, participantData);
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
            Map<String, List<ParticipantData>> participantData = null;
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
                    else { //source is not of any study-manager table so it must be ES
                        participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filters.get(source));
                        baseList = getCommonEntries(baseList, new ArrayList<>(participantESData.keySet()));
                    }
                }
            }
            //get all the list which were not filtered
            if (participantESData == null) {
                //get only pts for the filtered data
                if (baseList != null && !baseList.isEmpty()){
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
            if (kitRequests == null && DDPInstanceDao.getRole(instance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
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
            if (participantData == null) {
                participantData = ParticipantData.getParticipantData(instance.getName());
            }
            baseList = getCommonEntries(baseList, new ArrayList<>(participantESData.keySet()));

            //bring together all the information
            List<ParticipantWrapper> r = addAllData(baseList, participantESData, participants, medicalRecords, oncHistories, kitRequests, abstractionActivities, abstractionSummary, proxyData, participantData);
            return r;
        }
    }

    public static Optional<ParticipantWrapper> getParticipantFromESByHruid(DDPInstance ddpInstanceByRealm, String participantHruid) {
        Map<String, String> queryConditions = new HashMap<>();
        queryConditions.put("ES", ElasticSearchUtil.BY_HRUID + "'" + participantHruid + "'");
        List<ParticipantWrapper> participantsBelongToRealm = ParticipantWrapper.getFilteredList(ddpInstanceByRealm, queryConditions);
        return participantsBelongToRealm.stream().filter(Objects::nonNull).findFirst();
    }

    public static Optional<ParticipantWrapper> getParticipantFromESByLegacyShortId(DDPInstance ddpInstanceByRealm, String participantLegacyShortId) {
        Map<String, String> queryConditions = new HashMap<>();
        queryConditions.put("ES", ElasticSearchUtil.BY_LEGACY_SHORTID + "'" + participantLegacyShortId + "'");
        List<ParticipantWrapper> participantsBelongToRealm = ParticipantWrapper.getFilteredList(ddpInstanceByRealm, queryConditions);
        return participantsBelongToRealm.stream().filter(Objects::nonNull).findFirst();
    }

    public static String getParticipantGuid(Optional<ParticipantWrapper> maybeParticipant) {
        return maybeParticipant
                .map(p -> ((Map<String, String>)p.getData().get("profile")).get(ElasticSearchUtil.GUID))
                .orElse("");
    }

    public static String getParticipantLegacyAltPid(Optional<ParticipantWrapper> maybeParticipant) {
        return maybeParticipant
                .map(p -> ((Map<String, String>)p.getData().get("profile")).get(ElasticSearchUtil.LEGACY_ALT_PID))
                .orElse("");
    }

    public static Map<String, Map<String, Object>> getESData(@NonNull DDPInstance instance) {
        if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
            return ElasticSearchUtil.getDDPParticipantsFromES(instance.getName(), instance.getParticipantIndexES());
        }
        return null;
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
                                                      Map<String, Map<String, Object>> proxyData, Map<String, List<ParticipantData>> participantData) {
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
                        getProxyProfiles(participantESData, proxyData),
                participantData != null ? participantData.get(ddpParticipantId) : null));
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
}
