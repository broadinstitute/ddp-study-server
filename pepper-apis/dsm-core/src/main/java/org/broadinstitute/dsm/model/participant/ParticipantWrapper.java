package org.broadinstitute.dsm.model.participant;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.at.DefaultValues;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class ParticipantWrapper {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);

    public static final String BY_DDP_PARTICIPANT_ID_IN = " AND request.ddp_participant_id IN (\"";
    public static final String ORDER_AND_LIMIT = " ORDER BY request.dsm_kit_request_id desc LIMIT 5000";
    public static final int DEFAULT_ROWS_ON_PAGE = 50;

    private ParticipantWrapperPayload participantWrapperPayload;
    private ElasticSearchable elasticSearchable;

    private ElasticSearch esData = new ElasticSearch();
    private List<Participant> participants = new ArrayList<>();
    private Map<String, List<MedicalRecord>> medicalRecords = new HashMap<>();
    private Map<String, List<OncHistoryDetail>> oncHistoryDetails = new HashMap<>();
    private Map<String, List<KitRequestShipping>> kitRequests = new HashMap<>();
    private Map<String, List<AbstractionActivity>> abstractionActivities = new HashMap<>();
    private Map<String, List<AbstractionGroup>> abstractionSummary = new HashMap<>();
    private Map<String, List<ElasticSearchParticipantDto>> proxiesByParticipantIds = new HashMap<>();
    private Map<String, List<ParticipantDataDto>> participantData = new HashMap<>();

    public ParticipantWrapper(ParticipantWrapperPayload participantWrapperPayload, ElasticSearchable elasticSearchable) {
        this.participantWrapperPayload = Objects.requireNonNull(participantWrapperPayload);
        this.elasticSearchable = Objects.requireNonNull(elasticSearchable);
    }

    public ParticipantWrapperResult getFilteredList() {
        logger.info("Getting list of participant information");

        DDPInstanceDto ddpInstanceDto = participantWrapperPayload.getDdpInstanceDto()
                .orElseThrow();

        if (StringUtils.isBlank(ddpInstanceDto.getEsParticipantIndex())) {
            throw new RuntimeException("No participant index setup in ddp_instance table for " + ddpInstanceDto.getInstanceName());
        }

        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRole(ddpInstanceDto.getInstanceName(), DBConstants.HAS_MEDICAL_RECORD_ENDPOINTS);
        return participantWrapperPayload.getFilter()
                .map(filters -> {
                    fetchAndPrepareDataByFilters(ddpInstance, filters);
                    sortBySelfElseById(participantData.values());
                    return new ParticipantWrapperResult(esData.getTotalCount(), collectData());
                })
                .orElseGet(() -> {
                    fetchAndPrepareData(ddpInstance);
                    //if study is AT
                    if ("atcp".equals(ddpInstance.getName())) {
                        DefaultValues defaultValues = new DefaultValues(participantData, esData.getEsParticipants(), ddpInstance, null);
                        participantData = defaultValues.addDefaultValues();
                    }
                    sortBySelfElseById(participantData.values());
                    return new ParticipantWrapperResult(esData.getTotalCount(), collectData());
                });
    }

    //TODO could be better, good place for refactoring for future
    private void fetchAndPrepareDataByFilters(DDPInstance ddpInstance, Map<String, String> filters) {
        List<String> participantIdsToFetch = Collections.emptyList();
        for (String source : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(source))) {
                if (DBConstants.DDP_PARTICIPANT_ALIAS.equals(source)) {
                    Map<String, Participant> participants =
                            Participant.getParticipants(ddpInstance.getName(), filters.get(source));
                    this.participants = new ArrayList<>(participants.values());
                    participantIdsToFetch = new ArrayList<>(participants.keySet());
                }
                else if (DBConstants.DDP_MEDICAL_RECORD_ALIAS.equals(source)) {
                    medicalRecords = MedicalRecord.getMedicalRecords(ddpInstance.getName(), filters.get(source));
                    participantIdsToFetch = new ArrayList<>(medicalRecords.keySet());
                }
                else if (DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(source)) {
                    oncHistoryDetails = OncHistoryDetail.getOncHistoryDetails(ddpInstance.getName(), filters.get(source));
                    participantIdsToFetch = new ArrayList<>(oncHistoryDetails.keySet());
                }
                else if (DBConstants.DDP_KIT_REQUEST_ALIAS.equals(source)) {
                    kitRequests = KitRequestShipping.getKitRequests(ddpInstance, filters.get(source));
                    participantIdsToFetch = new ArrayList<>(kitRequests.keySet());
                }
                else if (DBConstants.DDP_PARTICIPANT_DATA_ALIAS.equals(source)) {
                    participantData = new ParticipantDataDao().getParticipantDataByInstanceIdAndFilterQuery(Integer.parseInt(ddpInstance.getDdpInstanceId()), filters.get(source));
                    participantIdsToFetch = new ArrayList<>(participantData.keySet());

                    //if study is AT TODO
                    if ("atcp".equals(ddpInstance.getName())) {
                        DefaultValues defaultValues =
                                new DefaultValues(participantData, esData.getEsParticipants(), ddpInstance, filters.get(source));
                        participantData = defaultValues.addDefaultValues();
                    }
                }
                else if (DBConstants.DDP_ABSTRACTION_ALIAS.equals(source)) {
                    abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(ddpInstance.getName(), filters.get(source));
                    participantIdsToFetch = new ArrayList<>(abstractionActivities.keySet());
                }
                else { //source is not of any study-manager table so it must be ES
                    esData = elasticSearchable.getParticipantsByRangeAndFilter(ddpInstance.getParticipantIndexES(), participantWrapperPayload.getFrom(),
                            participantWrapperPayload.getTo(), filters.get(source));
                    participantIdsToFetch = esData.getEsParticipants().stream().map(ElasticSearchParticipantDto::getParticipantId)
                            .collect(
                            Collectors.toList());
                }
            }
        }
        if (esData.getEsParticipants().isEmpty()) {
            esData = elasticSearchable.getParticipantsByRangeAndIds(ddpInstance.getParticipantIndexES(), participantWrapperPayload.getFrom(),
                    participantWrapperPayload.getTo(), participantIdsToFetch);
        }
        if (participants.isEmpty()) {
            participants = Participant.getParticipantsByIds(ddpInstance.getName(), participantIdsToFetch);
        }
        if (medicalRecords.isEmpty() && ddpInstance.isHasRole()) {
            medicalRecords = MedicalRecord.getMedicalRecordsByParticipantIds(ddpInstance.getName(), participantIdsToFetch);
        }
        if (oncHistoryDetails.isEmpty() && ddpInstance.isHasRole()) {
            oncHistoryDetails = OncHistoryDetail.getOncHistoryDetailsByParticipantIds(ddpInstance.getName(), participantIdsToFetch);
        }
        if (kitRequests.isEmpty() && DDPInstanceDao.getRole(ddpInstance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
            //get only kitRequests for the filtered pts
            if (Objects.nonNull(esData) && !esData.getEsParticipants().isEmpty()) {
                logger.info("About to query for kits from " + esData.getEsParticipants().size() + " participants");
                kitRequests = KitRequestShipping.getKitRequestsByParticipantIds(ddpInstance, participantIdsToFetch);
            }
        }
        if (participantData.isEmpty()) {
            participantData = new ParticipantDataDao().getParticipantDataByParticipantIds(participantIdsToFetch);

            //if study is AT
            if ("atcp".equals(ddpInstance.getName())) {
                DefaultValues defaultValues = new DefaultValues(participantData, esData.getEsParticipants(), ddpInstance, null);
                participantData = defaultValues.addDefaultValues();
            }
        }
        if (abstractionActivities.isEmpty()) {
            abstractionActivities = AbstractionActivity.getAllAbstractionActivityByRealm(ddpInstance.getName());
        }
        if (abstractionSummary.isEmpty()) {
            abstractionSummary = AbstractionFinal.getAbstractionFinal(ddpInstance.getName());
        }
        if (proxiesByParticipantIds.isEmpty()) {
            proxiesByParticipantIds = getProxiesWithParticipantIdsFromElasticList(ddpInstance.getUsersIndexES(), esData.getEsParticipants());
        }
    }

    private void fetchAndPrepareData(DDPInstance ddpInstance) {
        esData = elasticSearchable.getParticipantsWithinRange(
                ddpInstance.getParticipantIndexES(),
                participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo());
        List<String> participantIds = getParticipantIdsFromElasticList(esData.getEsParticipants());
        participants = Participant.getParticipantsByIds(ddpInstance.getName(), participantIds);
        if (ddpInstance.isHasRole()) {
            medicalRecords = MedicalRecord.getMedicalRecordsByParticipantIds(ddpInstance.getName(), participantIds);
            oncHistoryDetails = OncHistoryDetail.getOncHistoryDetailsByParticipantIds(ddpInstance.getName(), participantIds);
        }
        if (DDPInstanceDao.getRole(ddpInstance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) {
            kitRequests = KitRequestShipping.getKitRequestsByParticipantIds(ddpInstance, participantIds);
        }
        abstractionActivities =
                AbstractionActivity.getAllAbstractionActivityByParticipantIds(ddpInstance.getName(), participantIds);
        abstractionSummary = AbstractionFinal.getAbstractionFinalByParticipantIds(ddpInstance.getName(), participantIds);
        proxiesByParticipantIds = getProxiesWithParticipantIdsFromElasticList(ddpInstance.getUsersIndexES(), esData.getEsParticipants());
        participantData = new ParticipantDataDao().getParticipantDataByParticipantIds(participantIds);
    }

    Map<String, List<ElasticSearchParticipantDto>> getProxiesWithParticipantIdsFromElasticList(String esUsersIndex, List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> proxiesIdsFromElasticList = getProxiesIdsFromElasticList(elasticSearchParticipantDtos);
        return getProxiesWithParticipantIdsByProxiesIds(esUsersIndex, proxiesIdsFromElasticList);
    }

    private List<ParticipantWrapperDto> collectData() {
        logger.info("Collecting participant data...");
        List<ParticipantWrapperDto> result = new ArrayList<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            String participantId = elasticSearchParticipantDto.getParticipantId();
            if (StringUtils.isBlank(participantId)) continue;
            Participant participant = participants.stream()
                    .filter(ppt -> participantId.equals(ppt.getDdpParticipantId()))
                    .findFirst()
                    .orElse(null);
            result.add(new ParticipantWrapperDto(
                    elasticSearchParticipantDto, participant, medicalRecords.get(participantId),
                    oncHistoryDetails.get(participantId), kitRequests.get(participantId), abstractionActivities.get(participantId),
                    abstractionSummary.get(participantId), proxiesByParticipantIds.get(participantId), participantData.get(participantId)));
        }
        return result;
    }

    void sortBySelfElseById(Collection<List<ParticipantDataDto>> participantDatas) {
        participantDatas.forEach(pDataList -> pDataList.sort((o1, o2) -> {
            Map<String, String> pData = new Gson().fromJson(o1.getData().orElse(""), new TypeToken<Map<String, String>>() {}.getType());
            if (Objects.nonNull(pData) && FamilyMemberConstants.MEMBER_TYPE_SELF.equals(pData.get(FamilyMemberConstants.MEMBER_TYPE))) return -1;
            return o1.getParticipantDataId() - o2.getParticipantDataId();
        }));
    }

    List<String> getParticipantIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        return elasticSearchParticipantDtos.
                stream()
                .flatMap(elasticSearch -> elasticSearch.getProfile().stream())
                .map(ESProfile::getParticipantGuid)
                .collect(Collectors.toList());
    }

    Map<String, List<String>> getProxiesIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> participantsWithProxies = new HashMap<>();
        elasticSearchParticipantDtos.stream()
                .filter(esParticipantData -> esParticipantData.getProxies().orElse(Collections.emptyList()).size() > 0)
                .forEach(esParticipantData ->
                        participantsWithProxies.put(esParticipantData.getParticipantId(), esParticipantData.getProxies().get()));
        return participantsWithProxies;
    }

    Map<String, List<ElasticSearchParticipantDto>> getProxiesWithParticipantIdsByProxiesIds(String esUsersIndex,
                                                                                     Map<String, List<String>> proxiesIdsByParticipantIds) {
        Map<String, List<ElasticSearchParticipantDto>> proxiesByParticipantIds = new HashMap<>();
        List<String> proxiesIds = proxiesIdsByParticipantIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        List<ElasticSearchParticipantDto> participantsByIds = elasticSearchable.getParticipantsByIds(esUsersIndex, proxiesIds).getEsParticipants();
        participantsByIds.forEach(elasticSearchParticipantDto -> {
            String proxyId = elasticSearchParticipantDto.getParticipantId();
            for (Map.Entry<String, List<String>> entry: proxiesIdsByParticipantIds.entrySet()) {
                String participantId = entry.getKey();
                List<String> proxies = entry.getValue();
                if (proxies.contains(proxyId)) {
                    proxiesByParticipantIds.merge(participantId, new ArrayList<>(List.of(elasticSearchParticipantDto)), (prev, curr) -> {
                        prev.addAll(curr);
                        return prev;
                    });
                    break;
                }

            }
        });
        return proxiesByParticipantIds;
    }
}
