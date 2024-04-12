package org.broadinstitute.dsm.service.elastic;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROFILE_GUID;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROFILE_LEGACYALTPID;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.SourceMapDeserializer;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

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
     * Get a single participant document based on participant GUID. Throw an exception if document is not found.
     */
    public ElasticSearchParticipantDto getRequiredParticipantDocument(String ddpParticipantId, String index) {
        return getParticipantDocument(ddpParticipantId, index)
                .orElseThrow(() -> new ESMissingParticipantDataException("Participant document %s not found in index %s"
                        .formatted(ddpParticipantId, index)));
    }

    /**
     * Get a single participant document based on participant GUID
     */
    public Optional<ElasticSearchParticipantDto> getParticipantDocument(String ddpParticipantId, String index) {
        String matchField = ParticipantUtil.isGuid(ddpParticipantId) ? PROFILE_GUID : PROFILE_LEGACYALTPID;
        return getSingleParticipantDocument(ddpParticipantId, matchField, index);
    }

    public boolean participantDocumentExists(String ddpParticipantId, String index) {
        GetRequest getRequest = new GetRequest(index, ddpParticipantId);
        getRequest.fetchSourceContext(new FetchSourceContext(false));
        getRequest.storedFields("_none_");
        try {
            return ElasticSearchUtil.getClientInstance().exists(getRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new DsmInternalError("Error checking if participant document exists for %s, index: %s"
                    .formatted(ddpParticipantId, index), e);
        }
    }

    /**
     * Get a single participant document based on a field value match
     *
     * @param id value to match
     * @param matchField field to match against
     */
    public Optional<ElasticSearchParticipantDto> getSingleParticipantDocument(String id, String matchField,
                                                                              String index) {
        return getSingleParticipantDocument(id, matchField, null, index);
    }

    /**
     * Get a single participant document based on a field value match
     *
     * @param id value to match
     * @param matchField field to match against
     * @param includeField field to include in the response
     */
    public Optional<ElasticSearchParticipantDto> getSingleParticipantDocument(String id, String matchField,
                                                                              String includeField, String index) {
        SearchRequest searchRequest = new SearchRequest(index);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery(matchField, id));
        String[] includeFields = null;
        if (includeField != null) {
            includeFields = new String[] {includeField};
        }
        searchSourceBuilder.fetchSource(includeFields, null);
        searchRequest.source(searchSourceBuilder);

        SearchResponse response = search(searchRequest);
        log.info("ES search on index {}, match {} on {}", index, matchField, id);
        SearchHits hits = response.getHits();
        int numHits = hits.getHits().length;
        if (numHits == 0) {
            return Optional.empty();
        }
        if (numHits > 1) {
            throw new DsmInternalError("Multiple hits for match %s on %s in index %s"
                    .formatted(id, matchField, index));
        }
        return Optional.of(elasticSearch.parseSourceMap(hits.getAt(0).getSourceAsMap(), id));
    }

    /**
     * Return a map of participant guids to legacy PIDs for a participant ES index. This map only contains entries
     * for participants that have a legacy PID.
     */
    public Map<String, String> getLegacyPidsByGuid(String esIndex) {
        Map<String, String> guidToPid = new HashMap<>();

        SearchRequest searchRequest = new SearchRequest(esIndex);
        try {
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(QueryBuilders.existsQuery(PROFILE_LEGACYALTPID));
            searchRequest.source(sourceBuilder);

            int batchSize = 1000;
            int batchNumber = 0;
            while (true) {
                sourceBuilder.size(batchSize);
                sourceBuilder.from(batchNumber * batchSize);
                searchRequest.source(sourceBuilder);
                if (addLegacyPidHits(search(searchRequest), guidToPid, esIndex) == 0) {
                    break;
                }
                batchNumber++;
            }
        } catch (Exception e) {
            // this is likely due to a bad query, so log it
            log.error("Error getting ES participant legacy PIDs: index %s, query: %s"
                    .formatted(esIndex, searchRequest));
            throw new DsmInternalError("Error getting ES participants for instance " + esIndex, e);
        }
        log.info("Got {} participants from ES index {} (getLegacyPidsByGuid)", guidToPid.size(), esIndex);
        return guidToPid;
    }

    protected int addLegacyPidHits(SearchResponse response, Map<String, String> guidToPid, String esIndex) {
        SearchHits hits = response.getHits();
        int numHits = hits.getHits().length;
        for (int i = 0; i < numHits; i++) {
            SearchHit hit = hits.getAt(i);
            String ddpParticipantId = hit.getId();
            ElasticSearchParticipantDto participant =
                    elasticSearch.parseSourceMap(hit.getSourceAsMap(), ddpParticipantId);
            if (participant.getProfile().isPresent()) {
                Profile profile = participant.getProfile().get();
                String participantGuid = profile.getGuid();
                if (!participantGuid.equals(ddpParticipantId)) {
                    throw new DsmInternalError("Participant GUID does not match document ID: %s, %s. Index: %s"
                            .formatted(participantGuid, ddpParticipantId, esIndex));
                }
                if (StringUtils.isNotBlank(profile.getLegacyAltPid())) {
                    if (guidToPid.containsKey(ddpParticipantId)) {
                        throw new DsmInternalError("Duplicate participant GUID in Profile: %s, index: %s"
                                .formatted(participantGuid, esIndex));
                    }
                    guidToPid.put(ddpParticipantId, profile.getLegacyAltPid());
                }
            }
        }
        return numHits;
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
                if (addDsmHits(search(searchRequest), esData) == 0) {
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

    protected int addDsmHits(SearchResponse response, Map<String, Map<String, Object>> esData) {
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
        return getDsmData(ddpParticipantId, index).orElseThrow(() ->
                new ESMissingParticipantDataException("ES dsm data missing for participant %s and index %s"
                        .formatted(ddpParticipantId, index)));
    }

    /**
     * Get DSM data for a single participant in given index, throwing an exception if participant document is missing
     */
    public Optional<Dsm> getDsmData(String ddpParticipantId, String index) {
        if (!participantDocumentExists(ddpParticipantId, index)) {
            throw new ESMissingParticipantDataException("Participant document %s not found in index %s"
                    .formatted(ddpParticipantId, index));
        }
        Optional<ElasticSearchParticipantDto> esParticipant =
                getSingleParticipantDocument(ddpParticipantId, PROFILE_GUID, "dsm.*", index);
        if (esParticipant.isEmpty()) {
            return Optional.empty();
        }
        return esParticipant.get().getDsm();
    }

    /**
     * Get participant document as a string
     */
    public static Optional<String> getParticipantDocumentAsString(String ddpParticipantId, String index) {
        GetResponse res = _getParticipantDocument(index, ddpParticipantId);
        if (!res.isExists()) {
            return Optional.empty();
        }
        return Optional.ofNullable(res.getSourceAsString());
    }

    /**
     * Get participant document as an object map
     */
    public static Map<String, Object> getParticipantDocumentAsMap(String ddpParticipantId, String index) {
        GetResponse res = _getParticipantDocument(index, ddpParticipantId);
        if (!res.isExists()) {
            throw new DsmInternalError("Participant document %s not found in index %s".formatted(ddpParticipantId, index));
        }
        return res.getSource();
    }

    private static GetResponse _getParticipantDocument(String index, String ddpParticipantId) {
        try {
            GetRequest getRequest = new GetRequest().index(index).id(ddpParticipantId);
            return ElasticSearchUtil.getClientInstance().get(getRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new DsmInternalError("Error getting ES participant document %s for index %s"
                    .formatted(ddpParticipantId, index), e);
        }
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

    /**
     * Delete a participant document from the given index
     * @param docId the document ID (aka participant GUID)
     */
    public static void deleteParticipantDocument(String docId, String index) {
        try {
            DeleteRequest request = new DeleteRequest(index, docId);
            request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
            DeleteResponse deleteResponse =
                    ElasticSearchUtil.getClientInstance().delete(request, RequestOptions.DEFAULT);
            ReplicationResponse.ShardInfo shardInfo = deleteResponse.getShardInfo();
            if (shardInfo.getFailed() > 0) {
                String msg = (shardInfo.getFailures()[0]).reason();
                throw new DsmInternalError("Failed to delete ES participant document %s for index %s: %s"
                        .formatted(docId, index, msg));
            }
        } catch (ElasticsearchException | IOException e) {
            throw new DsmInternalError("Error deleting ES participant document %s for index %s"
                    .formatted(docId, index), e);
        }
    }
}
