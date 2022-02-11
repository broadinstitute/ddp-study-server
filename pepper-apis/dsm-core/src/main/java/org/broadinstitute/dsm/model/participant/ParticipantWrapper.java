package org.broadinstitute.dsm.model.participant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.query.DsmAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private Map<String, List<ParticipantData>> participantData = new HashMap<>();

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
                    return new ParticipantWrapperResult(esData.getTotalCount(), collectData(ddpInstance));
                })
                .orElseGet(() -> {
                    fetchAndPrepareData(ddpInstance);
                    sortBySelfElseById(participantData.values());
                    return new ParticipantWrapperResult(esData.getTotalCount(), collectData(ddpInstance));
                });
    }

    private void fetchAndPrepareDataByFilters(DDPInstance ddpInstance, Map<String, String> filters) {
        FilterParser parser = new FilterParser();
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (String source : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(source))) {
                if (isUnderDsmKey(source)) {
                    DsmAbstractQueryBuilder queryBuilder = new DsmAbstractQueryBuilder();
                    queryBuilder.setFilter(filters.get(source));
                    queryBuilder.setParser(parser);
                    boolQueryBuilder.must(queryBuilder.build());
                }
                else if (ElasticSearchUtil.ES.equals(source)){
                    //source is not of any study-manager table so it must be ES
                    boolQueryBuilder.must(ElasticSearchUtil.createESQuery(filters.get(source)));
                }
            }
        }
        esData = elasticSearchable.getParticipantsByRangeAndFilter(ddpInstance.getParticipantIndexES(), participantWrapperPayload.getFrom(),
                            participantWrapperPayload.getTo(), boolQueryBuilder);
    }

    private boolean isUnderDsmKey(String source) {
        return DBConstants.DDP_PARTICIPANT_ALIAS.equals(source)
                || DBConstants.DDP_MEDICAL_RECORD_ALIAS.equals(source)
                || DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(source)
                || DBConstants.DDP_KIT_REQUEST_ALIAS.equals(source)
                || DBConstants.DDP_TISSUE_ALIAS.equals(source)
                || DBConstants.DDP_ONC_HISTORY_ALIAS.equals(source)
                || DBConstants.DDP_PARTICIPANT_DATA_ALIAS.equals(source)
                || DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(source);
    }

    private void fetchAndPrepareData(DDPInstance ddpInstance) {
        esData = elasticSearchable.getParticipantsWithinRange(
                ddpInstance.getParticipantIndexES(),
                participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo());
        participants = getParticipantsFromEsData();
        if (ddpInstance.isHasRole()) {
            medicalRecords = getMedicalRecordsFromEsData();
            oncHistoryDetails = getOncHistoryDetailsFromEsData();
        }
        if (DDPInstanceDao.getRole(ddpInstance.getName(), DBConstants.KIT_REQUEST_ACTIVATED)) {
            kitRequests = getKitRequestsFromEsData();
        }
        proxiesByParticipantIds = getProxiesWithParticipantIdsFromElasticList(ddpInstance.getUsersIndexES(), esData.getEsParticipants());
        participantData = getParticipantDataFromEsData();
    }

    private Map<String, List<ParticipantData>> getParticipantDataFromEsData() {
        Map<String, List<ParticipantData>> participantDataByParticipantId = new HashMap<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            String participantId = elasticSearchParticipantDto.getParticipantId();
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> participantDataByParticipantId.put(participantId, esDsm.getParticipantData()));
        }
        return participantDataByParticipantId;
    }

    private Map<String, List<KitRequestShipping>> getKitRequestsFromEsData() {
        Map<String, List<KitRequestShipping>> kitRequestsByParticipantId = new HashMap<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            String participantId = elasticSearchParticipantDto.getParticipantId();
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> kitRequestsByParticipantId.put(participantId, esDsm.getKitRequestShipping()));
        }
        return kitRequestsByParticipantId;
    }

    private Map<String, List<OncHistoryDetail>> getOncHistoryDetailsFromEsData() {
        Map<String, List<OncHistoryDetail>> oncHistoryDetailsByParticipantId = new HashMap<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            String participantId = elasticSearchParticipantDto.getParticipantId();
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> oncHistoryDetailsByParticipantId.put(participantId, esDsm.getOncHistoryDetail()));
        }
        return oncHistoryDetailsByParticipantId;
    }

    private Map<String, List<MedicalRecord>> getMedicalRecordsFromEsData() {
        Map<String, List<MedicalRecord>> medicalRecordsByParticipantId = new HashMap<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            String participantId = elasticSearchParticipantDto.getParticipantId();
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> medicalRecordsByParticipantId.put(participantId, esDsm.getMedicalRecord()));
        }
        return medicalRecordsByParticipantId;
    }

    private List<Participant> getParticipantsFromEsData() {
        List<Participant> participants = new ArrayList<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
                Participant participant = esDsm.getParticipant().orElse(new Participant());
                if (Objects.nonNull(participant)) participants.add(participant);
            });
        }
        return participants;
    }

    Map<String, List<ElasticSearchParticipantDto>> getProxiesWithParticipantIdsFromElasticList(String esUsersIndex, List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> proxiesIdsFromElasticList = getProxiesIdsFromElasticList(elasticSearchParticipantDtos);
        return getProxiesWithParticipantIdsByProxiesIds(esUsersIndex, proxiesIdsFromElasticList);
    }

    private List<ParticipantWrapperDto> collectData(DDPInstance ddpInstance) {
        logger.info("Collecting participant data...");
        List<ParticipantWrapperDto> result = new ArrayList<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto: esData.getEsParticipants()) {

            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {

                Participant participant = esDsm.getParticipant().orElse(new Participant());

                esDsm.getOncHistory().ifPresent(oncHistory -> {
                    participant.setCreatedOncHistory(oncHistory.getCreated());
                    participant.setReviewedOncHistory(oncHistory.getReviewed());
                });

                List<MedicalRecord> medicalRecord = esDsm.getMedicalRecord();

                List<OncHistoryDetail> oncHistoryDetails = esDsm.getOncHistoryDetail();
                List<Tissue> tissues = esDsm.getTissue();
                mapTissueToProperOncHistoryDetail(oncHistoryDetails, tissues);

                List<KitRequestShipping> kitRequestShipping = esDsm.getKitRequestShipping();

                List<String> proxyGuids = elasticSearchParticipantDto.getProxies();
                String usersIndexES = ddpInstance.getUsersIndexES();
                ElasticSearch participantsByIds = elasticSearchable.getParticipantsByIds(usersIndexES, proxyGuids);
                List<ElasticSearchParticipantDto> proxies = participantsByIds.getEsParticipants();

                List<ParticipantData> participantData = esDsm.getParticipantData();

                ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
                participantWrapperDto.setEsData(elasticSearchParticipantDto);
                participantWrapperDto.setParticipant(participant);
                participantWrapperDto.setMedicalRecords(medicalRecord);
                participantWrapperDto.setOncHistoryDetails(oncHistoryDetails);
                participantWrapperDto.setKits(kitRequestShipping);
                participantWrapperDto.setProxyData(proxies);
                participantWrapperDto.setParticipantData(participantData);
                participantWrapperDto.setAbstractionActivities(Collections.emptyList());
                participantWrapperDto.setAbstractionSummary(Collections.emptyList());

                result.add(participantWrapperDto);

            });
        }
        return result;
    }

    private void mapTissueToProperOncHistoryDetail(List<OncHistoryDetail> oncHistoryDetails, List<Tissue> tissues) {
        for (Tissue tissue : tissues) {
            long oncHistoryDetailId = tissue.getOncHistoryDetailId();
            oncHistoryDetails.stream()
                    .filter(oncHistoryDetail -> oncHistoryDetail.getOncHistoryDetailId() == oncHistoryDetailId)
                    .findFirst()
                    .ifPresent(oncHistoryDetail -> oncHistoryDetail.getTissues().add(tissue));
        }
    }

    void sortBySelfElseById(Collection<List<ParticipantData>> participantDatas) {
        participantDatas.forEach(pDataList -> pDataList.sort((o1, o2) -> {
            Map<String, String> pData = new Gson().fromJson(o1.getData().orElse(StringUtils.EMPTY), new TypeToken<Map<String, String>>() {}.getType());
            if (Objects.nonNull(pData) && FamilyMemberConstants.MEMBER_TYPE_SELF.equals(pData.get(FamilyMemberConstants.MEMBER_TYPE))) return -1;
            return o1.getParticipantDataId() - o2.getParticipantDataId();
        }));
    }

    List<String> getParticipantIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        return elasticSearchParticipantDtos.
                stream()
                .flatMap(elasticSearch -> elasticSearch.getProfile().stream())
                .map(ESProfile::getGuid)
                .collect(Collectors.toList());
    }

    Map<String, List<String>> getProxiesIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> participantsWithProxies = new HashMap<>();
        elasticSearchParticipantDtos.stream()
                .filter(esParticipantData -> esParticipantData.getProxies().size() > 0)
                .forEach(esParticipantData ->
                        participantsWithProxies.put(esParticipantData.getParticipantId(), esParticipantData.getProxies()));
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
