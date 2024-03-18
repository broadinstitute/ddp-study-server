package org.broadinstitute.dsm.service.elastic;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.SourceMapDeserializer;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Service for interacting with ElasticSearch
 * </p>
 * Note: This is based on org.broadinstitute.dsm.model.elastic.search.ElasticSearch, and we move methods from there
 * as they get untangled from ElasticSearchUtil and come under automated testing
 */
@Slf4j
public class ElasticSearchService {
    private final ElasticSearch elasticSearch;
    private final SourceMapDeserializer deserializer;


    public ElasticSearchService() {
        this.elasticSearch = new ElasticSearch();
        this.deserializer = new SourceMapDeserializer();
        ElasticSearchUtil.initialize();
    }

    /**
     * Return a list of all participant guids in a participant ES index
     */
    public List<String> getAllParticipantGuids(String esParticipantsIndex) {
        ElasticSearch es = elasticSearch.getAllParticipantsDataByInstanceIndex(esParticipantsIndex);
        List<String> participantIds = new ArrayList<>();
        for (ElasticSearchParticipantDto participantDto : es.getEsParticipants()) {
            if (participantDto.getProfile().isPresent()) {
                Profile profile = participantDto.getProfile().get();
                if (StringUtils.isNotBlank(profile.getGuid())) {
                    participantIds.add(profile.getGuid());
                }
            }
        }
        log.info("Got {} participant ids from index (getAllParticipantGuids)", participantIds.size());
        return participantIds;
    }

    /**
     * Return a map of participant guids to legacy PIDs for a participant ES index. This map only contains entries
     * for participants that have a legacy PID.
     */
    public Map<String, String> getLegacyPidsByGuid(String esIndex) {
        SearchRequest searchRequest = new SearchRequest(esIndex);
        SearchResponse response;
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.existsQuery(ElasticSearchUtil.PROFILE_LEGACYALTPID));
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            // this is likely due to a bad query, so log it
            log.error("Error getting ES participant legacy PIDs: index %s, query: %s"
                    .formatted(esIndex, searchRequest));
            throw new DsmInternalError("Error getting ES participants for instance " + esIndex, e);
        }
        List<ElasticSearchParticipantDto> participants = elasticSearch.parseSourceMaps(response.getHits().getHits());
        log.info("Got {} participants from ES index {} (getLegacyPidsByGuid)", participants.size(), esIndex);

        Map<String, String> guidToPid = new HashMap<>();
        participants.forEach(participant -> {
            if (participant.getProfile().isPresent()) {
                Profile profile = participant.getProfile().get();
                String participantGuid = profile.getGuid();
                if (StringUtils.isNotBlank(participantGuid) && StringUtils.isNotBlank(profile.getLegacyAltPid())) {
                    if (guidToPid.containsKey(participantGuid)) {
                        throw new DsmInternalError("Duplicate participant GUID in Profile: %s, index: %s"
                                .formatted(participantGuid, esIndex));
                    }
                    guidToPid.put(profile.getGuid(), profile.getLegacyAltPid());
                }
            }
        });
        return guidToPid;
    }

    /**
     * Get DSM data for all participants in the given index
     *
     * @return map of participant GUID to document objectMap
     */
    public Map<String, Map<String, Object>> getAllDsmData(String index) {
        log.debug("Getting all participant DSM data for index {} (getAllDsmData)", index);
        Map<String, Map<String, Object>> esData = new HashMap<>();
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(QueryBuilders.matchAllQuery());
            String[] includeFields = new String[] {"dsm.*"};
            sourceBuilder.fetchSource(includeFields, null);
            SearchRequest searchRequest = new SearchRequest(index);

            int batchSize = 1000;
            int batchNumber = 0;
            while (true) {
                sourceBuilder.size(batchSize);
                sourceBuilder.from(batchNumber * batchSize);
                searchRequest.source(sourceBuilder);
                if (addHits(search(searchRequest), esData) == 0) {
                    break;
                }
                batchNumber++;
            }
        } catch (Exception e) {
            throw new DsmInternalError("Error getting participants from ES for index " + index, e);
        }
        log.info("Got {} participants from ES for index {} (getAllDsmData)", esData.size(), index);
        return esData;
    }

    protected int addHits(SearchResponse response, Map<String, Map<String, Object>> esData) {
        SearchHits hits = response.getHits();
        int numHits = hits.getHits().length;
        for (int i = 0; i < numHits; i++) {
            SearchHit hit = hits.getAt(i);
            String ddpParticipantId = hit.getId();
            Map<String, Object> dsmMap = (Map<String, Object>) hit.getSourceAsMap().get(ESObjectConstants.DSM);
            // dsmMap may be null if participant has no DSM data
            if (dsmMap != null) {
                deserializer.transformDsmSourceMap(dsmMap);
                esData.put(ddpParticipantId, dsmMap);
            }
        }
        return numHits;
    }

    /**
     * Convert Dsm ES participant source map to Dsm object
     */
    public Dsm deserializeDsmSourceMap(Map<String, Object> sourceMap) {
        return ObjectMapperSingleton.instance().convertValue(sourceMap, Dsm.class);
    }

    /**
     * Get DSM data for a single participant in the given index, throwing an exception if DSM data is missing
     */
    public Dsm getRequiredDsmData(String ddpParticipantId, String index) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(index, ddpParticipantId);
        return esParticipant.getDsm().orElseThrow(() ->
                new DsmInternalError("ES dsm data missing for participant %s and index %s".formatted(
                        ddpParticipantId, index)));
    }

    /**
     * Update DSM data for a participant in the given index
     */
    public static void updateDsm(String ddpParticipantId, Dsm dsm, String index) {
        Map<String, Object> dsmAsMap = ObjectMapperSingleton.instance().convertValue(dsm, Map.class);
        ElasticSearchUtil.updateRequest(ddpParticipantId, index,
                new HashMap<>(Map.of(ESObjectConstants.DSM, dsmAsMap)));
        log.info("Updated DSM data in Elastic for {}", ddpParticipantId);
    }

    /**
     * Update ParticipantData entities in ElasticSearch based on participantData list provided.
     * Note that participantDataList should be a list of ALL participant data for the participant.
     */
    public static void updateEsParticipantData(String ddpParticipantId, List<ParticipantData> participantDataList,
                                               DDPInstance instance) {
        ObjectTransformer objectTransformer = new ObjectTransformer(instance.getName());
        List<Map<String, Object>> transformedList =
                objectTransformer.transformObjectCollectionToCollectionMap((List) participantDataList);
        ElasticSearchUtil.updateRequest(ddpParticipantId, instance.getParticipantIndexES(),
                new HashMap<>(Map.of(ESObjectConstants.DSM,
                        new HashMap<>(Map.of(ESObjectConstants.PARTICIPANT_DATA, transformedList)))));
        log.info("Updated DSM participantData in Elastic for {}", ddpParticipantId);
    }
}
