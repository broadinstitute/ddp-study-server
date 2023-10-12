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
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.SomaticResultUpload;
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
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
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
        return getFilteredList(false);
    }

    public ParticipantWrapperResult getFilteredList(boolean noProxyDataNeeded) {
        logger.info("Getting list of participant information");

        DDPInstanceDto ddpInstanceDto = participantWrapperPayload.getDdpInstanceDto().orElseThrow();

        if (StringUtils.isBlank(ddpInstanceDto.getEsParticipantIndex())) {
            return new ParticipantWrapperResult(0, new ArrayList<>());
        }

        return participantWrapperPayload.getFilter().map(filters -> {
            fetchAndPrepareDataByFilters(filters, ddpInstanceDto.getInstanceName());
            return new ParticipantWrapperResult(esData.getTotalCount(), getParticipantWrapperDtoList(ddpInstanceDto, noProxyDataNeeded));
        }).orElseGet(() -> {
            fetchAndPrepareData();
            return new ParticipantWrapperResult(esData.getTotalCount(), getParticipantWrapperDtoList(ddpInstanceDto, noProxyDataNeeded));
        });
    }

    private List<ParticipantWrapperDto> getParticipantWrapperDtoList(DDPInstanceDto ddpInstanceDto, boolean noProxyDataNeeded) {
        if (noProxyDataNeeded) {
            return createParticipantWrapperDtoListWithoutProxy(ddpInstanceDto);
        }
        return collectProxyData(ddpInstanceDto);
    }

    private List<ParticipantWrapperDto> createParticipantWrapperDtoListWithoutProxy(DDPInstanceDto ddpInstanceDto) {
        List<ParticipantWrapperDto> result = new ArrayList<>();
        for (ElasticSearchParticipantDto elasticSearchParticipantDto : esData.getEsParticipants()) {
            addWrapperToList(elasticSearchParticipantDto, result, ddpInstanceDto);
        }
        return result;
    }

    public ParticipantWrapperResult getFilteredList(AbstractQueryBuilder<?> mainQuery) {
        logger.info("Getting list of participant information");

        DDPInstanceDto ddpInstanceDto = participantWrapperPayload.getDdpInstanceDto().orElseThrow();

        if (StringUtils.isBlank(ddpInstanceDto.getEsParticipantIndex())) {
            throw new RuntimeException("No participant index setup in ddp_instance table for " + ddpInstanceDto.getInstanceName());
        }

        fetchAndPrepareDataByAbstractQuery(mainQuery, ddpInstanceDto.getInstanceName());
        return new ParticipantWrapperResult(esData.getTotalCount(), collectProxyData(ddpInstanceDto));
    }

    private void fetchAndPrepareDataByAbstractQuery(AbstractQueryBuilder<?> mainQuery, String instanceName) {
        esData = elasticSearchable.getParticipantsByRangeAndFilter(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo(), mainQuery, instanceName);
    }

    private void fetchAndPrepareDataByFilters(Map<String, String> filters, String instanceName) {
        AbstractQueryBuilder<?> mainQuery = prepareQuery(filters);
        esData = elasticSearchable.getParticipantsByRangeAndFilter(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo(), mainQuery, instanceName);
    }

    AbstractQueryBuilder<?> prepareQuery(Map<String, String> filters) {
        AbstractQueryBuilder<?> mainQuery = new BoolQueryBuilder();
        boolean hasSeveralFilters = filters.size() > 1;
        for (String alias : filters.keySet()) {
            if (StringUtils.isNotBlank(filters.get(alias))) {
                BaseAbstractQueryBuilder queryBuilder = AbstractQueryBuilderFactory.create(filters.get(alias));
                queryBuilder.setEsIndex(getEsParticipantIndex());
                queryBuilder.setDdpInstanceId(getInstanceId());
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

    private Integer getInstanceId() {
        return participantWrapperPayload.getDdpInstanceDto().orElseThrow().getDdpInstanceId();
    }

    private void fetchAndPrepareData() {
        esData = elasticSearchable.getParticipantsWithinRange(getEsParticipantIndex(), participantWrapperPayload.getFrom(),
                participantWrapperPayload.getTo());
    }


    private List<ParticipantWrapperDto> collectProxyData(DDPInstanceDto ddpInstanceDto) {
        logger.info("Collecting participant proxy data...");
        List<ParticipantWrapperDto> result = new ArrayList<>();
        List<String> proxyGuids = new ArrayList<>();

        for (ElasticSearchParticipantDto elasticSearchParticipantDto : esData.getEsParticipants()) {
            if (elasticSearchParticipantDto instanceof UnparsedESParticipantDto) {
                addUnparsedWrapperToList((UnparsedESParticipantDto) elasticSearchParticipantDto, result, ddpInstanceDto);
            } else {
                addWrapperToList(elasticSearchParticipantDto, result, ddpInstanceDto);
            }
            proxyGuids.addAll(elasticSearchParticipantDto.getProxies());
        }
        fillParticipantWrapperDtosWithProxies(result, proxyGuids);
        return result;
    }

    private void filterOsteoFromOtherOsteoOutForExport(UnparsedESParticipantDto unparsedESParticipantDto, Integer ddpInstanceId) {
        Map<String, Object> dataAsMap = unparsedESParticipantDto.getDataAsMap();
        Object dsmObject = dataAsMap.get(ESObjectConstants.DSM);
        if (dsmObject != null) {
            filterData((Map<String, List<Map<String, Object>>>) dsmObject, ESObjectConstants.KIT_REQUEST_SHIPPING, ddpInstanceId);
            filterData((Map<String, List<Map<String, Object>>>) dsmObject, ESObjectConstants.MEDICAL_RECORD, ddpInstanceId);
            filterData((Map<String, List<Map<String, Object>>>) dsmObject, ESObjectConstants.ONC_HISTORY_DETAIL, ddpInstanceId);
            filterData((Map<String, List<Map<String, Object>>>) dsmObject, ESObjectConstants.SOMATIC_RESULT_UPLOAD, ddpInstanceId);
        }
        dataAsMap.size();
    }

    private void filterData(Map<String, List<Map<String, Object>>> dsmObject, String dsmObjectKey, Integer ddpInstanceId) {
        List<Map<String, Object>> dsmObjectListNew = new ArrayList<>();
        List<Map<String, Object>> dsmObjectList = dsmObject.get(dsmObjectKey);
        if (dsmObjectList != null && !dsmObjectList.isEmpty()) {
            for (Map<String, Object> dsmObjectMap : dsmObjectList) {
                Object ddpInstanceIdFromKitRequestShippingMap = dsmObjectMap.get(ESObjectConstants.DDP_INSTANCE_ID_CAMEL_CASE);
                if (ddpInstanceIdFromKitRequestShippingMap != null) {
                    if (ddpInstanceIdFromKitRequestShippingMap == ddpInstanceId) {
                        //only add the object if "ddpInstanceId" matches the id for that osteo instance
                        dsmObjectListNew.add(dsmObjectMap);
                    }
                } else {
                    //if the object doesn't have a "ddpInstanceId" key just add it back in
                    dsmObjectListNew.add(dsmObjectMap);
                }
            }
        }
        dsmObject.put(dsmObjectKey, dsmObjectListNew);
    }

    private void addUnparsedWrapperToList(UnparsedESParticipantDto elasticSearchParticipantDto, List<ParticipantWrapperDto> result,
                                          DDPInstanceDto ddpInstanceDto) {
        // don't do any copying of the main attributes, but do keep track of the proxies so that we can fetch them in bulk
        ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
        //if instance is osteo: filter out the sample information collected under the other study
        if (StringUtils.isNotBlank(ddpInstanceDto.getEsParticipantIndex())
                && ddpInstanceDto.getEsParticipantIndex().equals(DBConstants.OSTEO_INDEX)) {
            filterOsteoFromOtherOsteoOutForExport(elasticSearchParticipantDto, ddpInstanceDto.getDdpInstanceId());
        }
        participantWrapperDto.setEsData(elasticSearchParticipantDto);
        result.add(participantWrapperDto);
    }

    private void addWrapperToList(ElasticSearchParticipantDto elasticSearchParticipantDto, List<ParticipantWrapperDto> result,
                                  DDPInstanceDto ddpInstanceDto) {

        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
            Participant participant = esDsm.getParticipant().orElse(new Participant());

            List<ParticipantData> participantData = esDsm.getParticipantData();
            sortBySelfElseById(participantData);

            StudyPostFilter.fromPayload(StudyPostFilterPayload.of(elasticSearchParticipantDto, ddpInstanceDto))
                    .ifPresent(StudyPostFilter::filter);

            List<KitRequestShipping> kitRequestShipping = esDsm.getKitRequestShipping();

            ParticipantWrapperDto participantWrapperDto = new ParticipantWrapperDto();
            participantWrapperDto.setEsData(elasticSearchParticipantDto);
            participantWrapperDto.setParticipant(participant);
            participantWrapperDto.setParticipantData(participantData);

            esDsm.getOncHistory().ifPresent(oncHistory -> {
                participant.setCreated(oncHistory.getCreated());
                participant.setReviewed(oncHistory.getReviewed());
            });

            List<MedicalRecord> medicalRecord = esDsm.getMedicalRecord();
            List<OncHistoryDetail> oncHistoryDetails = esDsm.getOncHistoryDetail();
            oncHistoryDetails.removeIf(oncHistoryDetail -> oncHistoryDetail.isDeleted());
            List<SomaticResultUpload> somaticResultUpload = esDsm.getSomaticResultUpload();
            List<Tissue> tissues = esDsm.getTissue();
            tissues.removeIf(tissue -> tissue.isDeleted());
            List<SmId> smIds = esDsm.getSmId();
            smIds.removeIf(smId -> smId.getDeleted());
            List<ClinicalOrder> clinicalOrder = esDsm.getClinicalOrder();
            mapSmIdsToProperTissue(tissues, smIds);
            mapTissueToProperOncHistoryDetail(oncHistoryDetails, tissues);

            participantWrapperDto.setMedicalRecords(medicalRecord);
            participantWrapperDto.setOncHistoryDetails(oncHistoryDetails);
            participantWrapperDto.setSomaticResultUpload(somaticResultUpload);
            participantWrapperDto.setAbstractionActivities(Collections.emptyList());
            participantWrapperDto.setAbstractionSummary(Collections.emptyList());

            participantWrapperDto.setKits(kitRequestShipping);
            result.add(participantWrapperDto);
        });
    }


    private void mapSmIdsToProperTissue(List<Tissue> tissues, List<SmId> smIds) {
        for (SmId smId : smIds) {
            Integer tissueId = smId.getTissueId();
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
        if (participantProxyGuids == null) {
            return;
        }
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
