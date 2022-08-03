package org.broadinstitute.dsm.model.participant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.filter.query.AbstractQueryBuilderFactory;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilterPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantWrapper {

    private static final Logger logger = LoggerFactory.getLogger(ParticipantWrapper.class);

    private ParticipantWrapperPayload participantWrapperPayload;
    private ElasticSearchable elasticSearchable;

    private ElasticSearch esData = new ElasticSearch();

    public ParticipantWrapper(ParticipantWrapperPayload participantWrapperPayload, ElasticSearchable elasticSearchable) {
        this.participantWrapperPayload = Objects.requireNonNull(participantWrapperPayload);
        this.elasticSearchable = Objects.requireNonNull(elasticSearchable);
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
        AbstractQueryBuilder<?> mainQuery = prepareQuery(filters);
        esData = elasticSearchable.getParticipantsByRangeAndFilter(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo(), mainQuery);
    }

    AbstractQueryBuilder<?> prepareQuery(Map<String, String> filters) {
        AbstractQueryBuilder<?> mainQuery = new BoolQueryBuilder();
        boolean hasSeveralFilters = filters.size() > 1;
        for (String alias : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(alias))) {
                BaseAbstractQueryBuilder queryBuilder = AbstractQueryBuilderFactory.create(alias, filters.get(alias));
                queryBuilder.setEsIndex(getEsParticipantIndex());
                if (hasSeveralFilters) {
                    ((BoolQueryBuilder) mainQuery).must(queryBuilder.build());
                } else {
                    mainQuery = queryBuilder.build();
                }
            }
        }
        return mainQuery;
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

                mapSmIdsToProperTissue(tissues, smIds);

                mapTissueToProperOncHistoryDetail(oncHistoryDetails, tissues);

                proxyGuids.addAll(elasticSearchParticipantDto.getProxies());

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

                result.add(participantWrapperDto);

            });
        }
        fillParticipantWrapperDtosWithProxies(result, proxyGuids);
        return result;
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
            List<String> participantProxyGuids = participantWrapperDto.getEsData().getProxies();
            List<ElasticSearchParticipantDto> proxyEsData = proxiesByIds.getEsParticipants().stream()
                    .filter(elasticSearchParticipantDto -> participantProxyGuids.contains(elasticSearchParticipantDto.getParticipantId()))
                    .collect(Collectors.toList());
            participantWrapperDto.setProxyData(proxyEsData);
        });
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
        return elasticSearchParticipantDtos.stream().flatMap(elasticSearch -> elasticSearch.getProfile().stream()).map(Profile::getGuid)
                .collect(Collectors.toList());
    }

    Map<String, List<String>> getProxiesIdsFromElasticList(List<ElasticSearchParticipantDto> elasticSearchParticipantDtos) {
        Map<String, List<String>> participantsWithProxies = new HashMap<>();
        elasticSearchParticipantDtos.stream().filter(esParticipantData -> esParticipantData.getProxies().size() > 0).forEach(
                esParticipantData -> participantsWithProxies.put(esParticipantData.getParticipantId(), esParticipantData.getProxies()));
        return participantsWithProxies;
    }

}
