package org.broadinstitute.dsm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantWrapper {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);

    public static final String BY_DDP_PARTICIPANT_ID_IN = " AND request.ddp_participant_id IN (\"";
    public static final String ORDER_AND_LIMIT = " ORDER BY request.dsm_kit_request_id desc LIMIT 5000";
    public static final int INDEX_AFTER_PROXY = 6;

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
                              List<OncHistoryDetail> oncHistoryDetails, List<KitRequestShipping> kits,
                              List<AbstractionActivity> abstractionActivities, List<AbstractionGroup> abstractionSummary,
                              List<Map<String, Object>> proxyData, List<ParticipantData> participantData) {
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

    public ParticipantWrapper() {
    }

    public static Map<String, Map<String, Object>> getESData(@NonNull DDPInstance instance) {
        if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
            return ElasticSearchUtil.getDDPParticipantsFromES(instance.getName(), instance.getParticipantIndexES());
        }
        return null;
    }

    public static List<ParticipantWrapper> addAllData(List<String> baseList, Map<String, Map<String, Object>> esDataMap,
                                                      Map<String, Participant> participantMap,
                                                      Map<String, List<MedicalRecord>> medicalRecordMap,
                                                      Map<String, List<OncHistoryDetail>> oncHistoryMap,
                                                      Map<String, List<KitRequestShipping>> kitRequestMap,
                                                      Map<String, List<AbstractionActivity>> abstractionActivityMap,
                                                      Map<String, List<AbstractionGroup>> abstractionSummary,
                                                      Map<String, Map<String, Object>> proxyData,
                                                      Map<String, List<ParticipantData>> participantData) {
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

    private static List<Map<String, Object>> getProxyProfiles(Map<String, Object> participantData,
                                                              Map<String, Map<String, Object>> proxyDataES) {
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
