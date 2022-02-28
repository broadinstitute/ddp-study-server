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
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.Tissue;
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

    public static final String BY_DDP_PARTICIPANT_ID_IN = " AND request.ddp_participant_id IN (\"";
    public static final String ORDER_AND_LIMIT = " ORDER BY request.dsm_kit_request_id desc LIMIT 5000";
    public static final int DEFAULT_ROWS_ON_PAGE = 50;
    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);
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

        DDPInstanceDto ddpInstanceDto = participantWrapperPayload.getDdpInstanceDto().orElseThrow();

        if (StringUtils.isBlank(ddpInstanceDto.getEsParticipantIndex())) {
            throw new RuntimeException("No participant index setup in ddp_instance table for " + ddpInstanceDto.getInstanceName());
        }

        return participantWrapperPayload.getFilter().map(filters -> {
            fetchAndPrepareDataByFilters(ddpInstanceDto.getEsParticipantIndex(), ddpInstanceDto.getEsUsersIndex(), filters);
            sortBySelfElseById(participantData.values());
            return new ParticipantWrapperResult(esData.getTotalCount(), collectData());
        }).orElseGet(() -> {
            fetchAndPrepareData(ddpInstanceDto.getEsParticipantIndex(), ddpInstanceDto.getEsUsersIndex());
            sortBySelfElseById(participantData.values());
            return new ParticipantWrapperResult(esData.getTotalCount(), collectData());
        });
    }

    private void fetchAndPrepareDataByFilters(String participantIndexES, String userIndexES, Map<String, String> filters) {
        logger.info("Fetch participant data by filters...");
        FilterParser parser = new FilterParser();
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (String source : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(source))) {
                if (isUnderDsmKey(source)) {
                    DsmAbstractQueryBuilder queryBuilder = new DsmAbstractQueryBuilder();
                    queryBuilder.setFilter(filters.get(source));
                    queryBuilder.setParser(parser);
                    boolQueryBuilder.must(queryBuilder.build());
                } else if (ElasticSearchUtil.ES.equals(source)) {
                    //source is not of any study-manager table so it must be ES
                    boolQueryBuilder.must(ElasticSearchUtil.createESQuery(filters.get(source)));
                }
            }
        }
        esData = elasticSearchable.getParticipantsByRangeAndFilter(participantIndexES, participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo(), boolQueryBuilder);
        if (StringUtils.isNotBlank(userIndexES)) {
            logger.info("Fetch proxy data...");
            proxiesByParticipantIds =
                    getProxiesWithParticipantIdsFromElasticList(userIndexES, esData.getEsParticipants());
        }
    }

    private boolean isUnderDsmKey(String source) {
        return DBConstants.DDP_PARTICIPANT_ALIAS.equals(source) || DBConstants.DDP_MEDICAL_RECORD_ALIAS.equals(source)
                || DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS.equals(source) || DBConstants.DDP_KIT_REQUEST_ALIAS.equals(source)
                || DBConstants.DDP_TISSUE_ALIAS.equals(source) || DBConstants.DDP_ONC_HISTORY_ALIAS.equals(source)
                || DBConstants.DDP_PARTICIPANT_DATA_ALIAS.equals(source) || DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(source);
    }

    private void fetchAndPrepareData(String participantIndexES, String userIndexES) {
        logger.info("Fetch participant data...");
        esData = elasticSearchable.getParticipantsWithinRange(participantIndexES, participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo());
        if (StringUtils.isNotBlank(userIndexES)) {
            logger.info("Fetch proxy data...");
            proxiesByParticipantIds =
                    getProxiesWithParticipantIdsFromElasticList(userIndexES, esData.getEsParticipants());
        }
    }

    Map<String, List<ElasticSearchParticipantDto>> getProxiesWithParticipantIdsFromElasticList(String esUsersIndex,
                                                                                               List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> proxiesIdsFromElasticList = getProxiesIdsFromElasticList(elasticSearchParticipantDtos);
        return getProxiesWithParticipantIdsByProxiesIds(esUsersIndex, proxiesIdsFromElasticList);
    }

    private List<ParticipantWrapperDto> collectData() {
        logger.info("Reorganize ES data for frontend");

        List<ParticipantWrapperDto> result = new ArrayList<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto : esData.getEsParticipants()) {
            //moving objects around from ES dsm object to where the frontend is expecting them
            elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {

                Participant participant = esDsm.getParticipant().orElse(new Participant());

                List<ParticipantData> participantData = esDsm.getParticipantData();

                ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
                participantWrapperDto.setEsData(elasticSearchParticipantDto);
                participantWrapperDto.setParticipant(participant);
                participantWrapperDto.setParticipantData(participantData);

                List<MedicalRecord> medicalRecord = esDsm.getMedicalRecord();
                participantWrapperDto.setMedicalRecords(medicalRecord);

                esDsm.getOncHistory().ifPresent(oncHistory -> {
                    participant.setCreatedOncHistory(oncHistory.getCreated());
                    participant.setReviewedOncHistory(oncHistory.getReviewed());
                });

                List<OncHistoryDetail> oncHistoryDetails = esDsm.getOncHistoryDetail();
                List<Tissue> tissues = esDsm.getTissue();
                mapTissueToProperOncHistoryDetail(oncHistoryDetails, tissues);
                participantWrapperDto.setOncHistoryDetails(oncHistoryDetails);

                participantWrapperDto.setAbstractionActivities(Collections.emptyList());
                participantWrapperDto.setAbstractionSummary(Collections.emptyList());

                List<KitRequestShipping> kitRequestShipping = esDsm.getKitRequestShipping();
                participantWrapperDto.setKits(kitRequestShipping);

                ESProfile esProfile = elasticSearchParticipantDto.getProfile().orElseThrow();
                String ddpParticipantId = esProfile.getGuid();
                if (proxiesByParticipantIds != null) {
                    List<ElasticSearchParticipantDto> proxies = proxiesByParticipantIds.get(ddpParticipantId);
                    if (proxies != null && !proxies.isEmpty()) {
                        participantWrapperDto.setProxyData(proxies);
                    }
                }
                result.add(participantWrapperDto);
            });
        }
        return result;
    }

    private void mapTissueToProperOncHistoryDetail(List<OncHistoryDetail> oncHistoryDetails, List<Tissue> tissues) {
        for (Tissue tissue : tissues) {
            long oncHistoryDetailId = tissue.getOncHistoryDetailId();
            oncHistoryDetails.stream().filter(oncHistoryDetail -> oncHistoryDetail.getOncHistoryDetailId() == oncHistoryDetailId)
                    .findFirst().ifPresent(oncHistoryDetail -> oncHistoryDetail.getTissues().add(tissue));
        }
    }

    void sortBySelfElseById(Collection<List<ParticipantData>> participantDatas) {
        participantDatas.forEach(pDataList -> pDataList.sort((o1, o2) -> {
            Map<String, String> participantData =
                    new Gson().fromJson(o1.getData().orElse(StringUtils.EMPTY), new TypeToken<Map<String, String>>() {
                    }.getType());
            if (Objects.nonNull(participantData) && FamilyMemberConstants.MEMBER_TYPE_SELF.equals(
                    participantData.get(FamilyMemberConstants.MEMBER_TYPE))) {
                return -1;
            }
            return o1.getParticipantDataId() - o2.getParticipantDataId();
        }));
    }

    List<String> getParticipantIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        return elasticSearchParticipantDtos.stream().flatMap(elasticSearch -> elasticSearch.getProfile().stream()).map(ESProfile::getGuid)
                .collect(Collectors.toList());
    }

    Map<String, List<String>> getProxiesIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> participantsWithProxies = new HashMap<>();
        elasticSearchParticipantDtos.stream().filter(esParticipantData -> esParticipantData.getProxies().size() > 0).forEach(
                esParticipantData -> participantsWithProxies.put(esParticipantData.getParticipantId(), esParticipantData.getProxies()));
        return participantsWithProxies;
    }

    Map<String, List<ElasticSearchParticipantDto>> getProxiesWithParticipantIdsByProxiesIds(String esUsersIndex,
                                                                                            Map<String, List<String>> proxiesIdsByParticipantIds) {
        Map<String, List<ElasticSearchParticipantDto>> proxiesByParticipantIds = new HashMap<>();
        List<String> proxiesIds = proxiesIdsByParticipantIds.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        List<ElasticSearchParticipantDto> participantsByIds =
                elasticSearchable.getParticipantsByIds(esUsersIndex, proxiesIds).getEsParticipants();
        participantsByIds.forEach(elasticSearchParticipantDto -> {
            String proxyId = elasticSearchParticipantDto.getParticipantId();
            for (Map.Entry<String, List<String>> entry : proxiesIdsByParticipantIds.entrySet()) {
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
