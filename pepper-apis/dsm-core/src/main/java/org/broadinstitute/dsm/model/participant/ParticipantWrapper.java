package org.broadinstitute.dsm.model.participant;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.query.DsmAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilterPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class ParticipantWrapper {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);

    private ParticipantWrapperPayload participantWrapperPayload;
    private ElasticSearchable elasticSearchable;

    private ElasticSearch esData = new ElasticSearch();

    public ParticipantWrapper(ParticipantWrapperPayload participantWrapperPayload, ElasticSearchable elasticSearchable) {
        this.participantWrapperPayload = Objects.requireNonNull(participantWrapperPayload);
        this.elasticSearchable = Objects.requireNonNull(elasticSearchable);
        // use the same deserializer as the search results for parsing the proxy data
        this.esData.setDeserializer(elasticSearchable.getDeserializer());
        participantWrapperPayload.getSortBy().ifPresent(sortBy -> {
            FieldTypeExtractor fieldTypeExtractor = new FieldTypeExtractor();
            fieldTypeExtractor.setIndex(participantWrapperPayload.getDdpInstanceDto().orElseThrow().getEsParticipantIndex());
            Sort sort = Sort.of(sortBy, fieldTypeExtractor);
            elasticSearchable.setSortBy(sort);
        });
    }

    public ParticipantWrapperResult getFilteredList() {
        logger.info("Getting list of participant information");

        DDPInstanceDto ddpInstanceDto = participantWrapperPayload.getDdpInstanceDto().orElseThrow();

        if (StringUtils.isBlank(ddpInstanceDto.getEsParticipantIndex())) {
            throw new RuntimeException("No participant index setup in ddp_instance table for " + ddpInstanceDto.getInstanceName());
        }

        return participantWrapperPayload.getFilter().map(filters -> {
            fetchAndPrepareDataByFilters(filters);
            return new ParticipantWrapperResult(esData.getTotalCount(), collectData(ddpInstanceDto));
        }).orElseGet(() -> {
            fetchAndPrepareData();
            return new ParticipantWrapperResult(esData.getTotalCount(), collectData(ddpInstanceDto));
        });
    }

    private void fetchAndPrepareDataByFilters(Map<String, String> filters) {
        FilterParser parser = new FilterParser();
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (String source : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(source))) {
                if (Util.isUnderDsmKey(source)) {
                    DsmAbstractQueryBuilder queryBuilder = new DsmAbstractQueryBuilder();
                    queryBuilder.setFilter(filters.get(source));
                    queryBuilder.setParser(parser);
                    queryBuilder.setEsIndex(getEsParticipantIndex());
                    boolQueryBuilder.must(queryBuilder.build());
                } else if (ElasticSearchUtil.ES.equals(source)) {
                    //source is not of any study-manager table so it must be ES
                    boolQueryBuilder.must(ElasticSearchUtil.createESQuery(filters.get(source)));
                }
            }
        }
        esData = elasticSearchable.getParticipantsByRangeAndFilter(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo(), boolQueryBuilder);
    }

    private String getEsParticipantIndex() {
        return participantWrapperPayload.getDdpInstanceDto().orElseThrow().getEsParticipantIndex();
    }

    private void fetchAndPrepareData() {
        esData = elasticSearchable.getParticipantsWithinRange(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo());
    }


    private List<ParticipantWrapperDto> collectData(DDPInstanceDto ddpInstanceDto) {
        logger.info("Collecting participant data...");
        List<ParticipantWrapperDto> result = new ArrayList<>();
        List<String> proxyGuids = new ArrayList<>();

        for (ElasticSearchParticipantDto elasticSearchParticipantDto : esData.getEsParticipants()) {
            if (elasticSearchParticipantDto instanceof UnparsedESParticipantDto) {
                addWrapperToList((UnparsedESParticipantDto) elasticSearchParticipantDto, result);
            } else {
                addWrapperToList(elasticSearchParticipantDto, result, ddpInstanceDto);
            }
            proxyGuids.addAll(elasticSearchParticipantDto.getProxies());
        }
        fillParticipantWrapperDtosWithProxies(result, proxyGuids);
        return result;
    }

    private void addWrapperToList(UnparsedESParticipantDto elasticSearchParticipantDto, List<ParticipantWrapperDto> result) {
        // don't do any copying of the main attributes, but do keep track of the proxies so that we can fetch them in bulk
        ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
        participantWrapperDto.setEsData(elasticSearchParticipantDto);
        result.add(participantWrapperDto);
    }

    private void addWrapperToList(ElasticSearchParticipantDto elasticSearchParticipantDto, List<ParticipantWrapperDto> result, DDPInstanceDto ddpInstanceDto) {

        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
            Participant participant = esDsm.getParticipant().orElse(new Participant());

            esDsm.getOncHistory().ifPresent(oncHistory -> {
                participant.setCreated(oncHistory.getCreated());
                participant.setReviewed(oncHistory.getReviewed());
            });

            StudyPreFilter.fromPayload(StudyPreFilterPayload.of(elasticSearchParticipantDto, ddpInstanceDto))
                    .ifPresent(StudyPreFilter::filter);

            List<MedicalRecord> medicalRecord = esDsm.getMedicalRecord();
            List<OncHistoryDetail> oncHistoryDetails = esDsm.getOncHistoryDetail();
            List<KitRequestShipping> kitRequestShipping = esDsm.getKitRequestShipping();
            List<Tissue> tissues = esDsm.getTissue();
            List<SmId> smIds = esDsm.getSmId();
            List<ClinicalOrder> clinicalOrder = esDsm.getClinicalOrder();

            mapSmIdsToProperTissue(tissues, smIds);

            mapTissueToProperOncHistoryDetail(oncHistoryDetails, tissues);



            List<ParticipantData> participantData = esDsm.getParticipantData();
            sortBySelfElseById(participantData);
            ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
            participantWrapperDto.setEsData(elasticSearchParticipantDto);
            participantWrapperDto.setParticipant(participant);
            participantWrapperDto.setMedicalRecords(medicalRecord);
            participantWrapperDto.setOncHistoryDetails(oncHistoryDetails);
            participantWrapperDto.setKits(kitRequestShipping);
            participantWrapperDto.setParticipantData(participantData);
            participantWrapperDto.setAbstractionActivities(Collections.emptyList());
            participantWrapperDto.setAbstractionSummary(Collections.emptyList());
            participantWrapperDto.setClinicalOrder(clinicalOrder);
            result.add(participantWrapperDto);
        });
    }


    private void mapSmIdsToProperTissue(List<Tissue> tissues, List<SmId> smIds) {
        for (SmId smId : smIds) {
            Long tissueId = smId.getTissueId();
            tissues.stream().filter(tissue -> tissue.getTissueId().equals(tissueId)).findFirst()
                    .ifPresent(tissue -> fillSmIdsByType(smId, tissue));
        }
    }

    private void fillSmIdsByType(SmId smId, Tissue tissue) {
        String smIdType = smId.getSmIdType();
        if (SmId.HE.equals(smIdType)) {
            tissue.getHeSMID().add(smId);
        } else if (SmId.SCROLLS.equals(smIdType)) {
            tissue.getScrollSMID().add(smId);
        } else if (SmId.USS.equals(smIdType)) {
            tissue.getUssSMID().add(smId);
        }
    }

    //method to avoid ES request for each participant's proxy
    void fillParticipantWrapperDtosWithProxies(List<ParticipantWrapperDto> result, List<String> proxyGuids) {
        String esUsersIndex = participantWrapperPayload.getDdpInstanceDto().orElseThrow().getEsUsersIndex();
        SortBy profileCreatedAt =
                new SortBy.Builder().withType(Filter.NUMBER).withOrder("asc").withInnerProperty(ElasticSearchUtil.CREATED_AT)
                        .withOuterProperty(ElasticSearchUtil.PROFILE).withTableAlias(ElasticSearchUtil.DATA).build();
        FieldTypeExtractor fieldTypeExtractor = new FieldTypeExtractor();
        fieldTypeExtractor.setIndex(esUsersIndex);
        Sort sort = Sort.of(profileCreatedAt, fieldTypeExtractor);
        elasticSearchable.setSortBy(sort);
        ElasticSearch proxiesByIds = elasticSearchable.getParticipantsByIds(esUsersIndex, proxyGuids);
        result.forEach(participantWrapperDto -> {
            if (participantWrapperDto.getEsData() instanceof UnparsedESParticipantDto) {
                addProxyDataToDto((UnparsedESParticipantDto) participantWrapperDto.getEsData(), proxiesByIds);
            } else {
                addProxyDataToDto(participantWrapperDto, proxiesByIds);
            }
        });
    }

    private void addProxyDataToDto(ParticipantWrapperDto participantWrapperDto, ElasticSearch proxiesByIds) {
        List<String> participantProxyGuids = participantWrapperDto.getEsData().getProxies();
        List<ElasticSearchParticipantDto> proxyEsData = proxiesByIds.getEsParticipants().stream()
                .filter(elasticSearchParticipantDto -> participantProxyGuids.contains(elasticSearchParticipantDto.getParticipantId()))
                .collect(Collectors.toList());
        participantWrapperDto.setProxyData(proxyEsData);
    }

    private void addProxyDataToDto(UnparsedESParticipantDto esDto, ElasticSearch proxiesByIds) {
        List<String> participantProxyGuids = (List<String>) esDto.getDataAsMap().get("proxies");
        List<ElasticSearchParticipantDto> proxyEsData = proxiesByIds.getEsParticipants().stream()
                .filter(proxyEsDto -> {
                    Map<String, Object> profile = (Map<String, Object>) ((UnparsedESParticipantDto) proxyEsDto).getDataAsMap()
                            .get(ESObjectConstants.PROFILE);
                    return participantProxyGuids.contains((String) profile.get(ESObjectConstants.GUID));
                }).collect(Collectors.toList());
        List<Map<String, Object>> proxyEsDataAsMaps = proxyEsData.stream()
                .map(proxyData -> ((UnparsedESParticipantDto) proxyData).getDataAsMap()).collect(Collectors.toList());
        esDto.getDataAsMap().put(ESObjectConstants.PROXY_DATA, proxyEsDataAsMaps);
    }

    private void mapTissueToProperOncHistoryDetail(List<OncHistoryDetail> oncHistoryDetails, List<Tissue> tissues) {
        for (Tissue tissue : tissues) {
            long oncHistoryDetailId = tissue.getOncHistoryDetailId();
            oncHistoryDetails.stream().filter(oncHistoryDetail -> oncHistoryDetail.getOncHistoryDetailId() == oncHistoryDetailId)
                    .findFirst().ifPresent(oncHistoryDetail -> oncHistoryDetail.getTissues().add(tissue));
        }
    }

    void sortBySelfElseById(List<ParticipantData> participantDatas) {
        participantDatas.sort((o1, o2) -> {
            Map<String, String> ptData =
                    ObjectMapperSingleton.readValue(o1.getData().orElse(StringUtils.EMPTY), new TypeReference<Map<String, String>>() {
                    });
            Map<String, String> ptData2 =
                    ObjectMapperSingleton.readValue(o2.getData().orElse(StringUtils.EMPTY), new TypeReference<Map<String, String>>() {
                    });
            if (Objects.nonNull(ptData) && FamilyMemberConstants.MEMBER_TYPE_SELF.equals(ptData.get(FamilyMemberConstants.MEMBER_TYPE))) {
                return -1;
            }
            if (Objects.nonNull(ptData2) && FamilyMemberConstants.MEMBER_TYPE_SELF.equals(ptData2.get(FamilyMemberConstants.MEMBER_TYPE))) {
                return 1;
            }
            return o1.getParticipantDataId() - o2.getParticipantDataId();
        });
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

}
