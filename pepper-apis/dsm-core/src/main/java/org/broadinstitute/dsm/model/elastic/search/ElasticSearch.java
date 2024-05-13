package org.broadinstitute.dsm.model.elastic.search;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.ACTIVITY_CODE;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.ACTIVITY_VERSION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.sort.CustomSortBuilder;
import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setter
public class ElasticSearch implements ElasticSearchable {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
    private Deserializer deserializer;
    private SortBuilder sortBy;

    List<ElasticSearchParticipantDto> esParticipants;
    long totalCount;


    public ElasticSearch() {
        this.sortBy = SortBuilders.fieldSort(ElasticSearchUtil.PROFILE_CREATED_AT).order(SortOrder.DESC)
                .unmappedType(CustomSortBuilder.UNMAPPED_TYPE_LONG).missing(CustomSortBuilder.IF_MISSING_LAST);
        this.deserializer = new SourceMapDeserializer();
    }

    public ElasticSearch(List<ElasticSearchParticipantDto> esParticipants, long totalCount) {
        this();
        this.esParticipants = esParticipants;
        this.totalCount = totalCount;
    }

    @Override
    public void setDeserializer(Deserializer deserializer) {
        this.deserializer = deserializer;
    }

    @Override
    public Deserializer getDeserializer() {
        return this.deserializer;
    }

    @Override
    public void setSortBy(Sort sort) {
        if (Objects.nonNull(sort)) {
            this.sortBy = new CustomSortBuilder(sort);
        }
    }

    public List<ElasticSearchParticipantDto> getEsParticipants() {
        if (Objects.isNull(esParticipants)) {
            esParticipants = Collections.emptyList();
        }
        return esParticipants;
    }

    public long getTotalCount() {
        return totalCount;
    }

    /**
     * Parses the result map to create a new {@link ElasticSearchParticipantDto}.
     * @param sourceMap the source map
     * @param queriedParticipantId optional, used for troubleshooting.  If given, this becomes the queriedParticipantId
     * @return a new {@link ElasticSearchParticipantDto} from the source map
     */
    public ElasticSearchParticipantDto parseSourceMap(Map<String, Object> sourceMap, String queriedParticipantId) {
        if (sourceMap != null) {
            Optional<ElasticSearchParticipantDto> esParticipantDto =
                    ElasticSearchParticipantDto.fromSourceMap(sourceMap, deserializer);
            if (esParticipantDto.isPresent()) {
                var participantDto = esParticipantDto.get();
                participantDto.setQueriedParticipantId(queriedParticipantId);
                return esParticipantDto.get();
            }
        }
        return new ElasticSearchParticipantDto.Builder().withQueriedParticipantId(queriedParticipantId).build();
    }

    public List<ElasticSearchParticipantDto> parseSourceMaps(SearchHit[] searchHits) {
        if (Objects.isNull(searchHits)) {
            return Collections.emptyList();
        }
        List<ElasticSearchParticipantDto> result = new ArrayList<>();
        String ddp = getDdpFromSearchHit(Arrays.stream(searchHits).findFirst().orElse(null));
        for (SearchHit searchHit : searchHits) {
            ElasticSearchParticipantDto participantDto = parseSourceMap(searchHit.getSourceAsMap(), null);
            participantDto.setDdp(ddp);
            result.add(participantDto);
        }
        return result;
    }

    private String getDdpFromSearchHit(SearchHit searchHit) {
        if (Objects.isNull(searchHit)) {
            return "";
        }
        return getDdpFromIndex(searchHit.getIndex());
    }

    String getDdpFromIndex(String searchHitIndex) {
        if (StringUtils.isBlank(searchHitIndex)) {
            return "";
        }
        int dotIndex = searchHitIndex.lastIndexOf('.');
        return searchHitIndex.substring(dotIndex + 1);
    }

    @Override
    public ElasticSearch getParticipantsWithinRange(String esParticipantsIndex, int from, int to) {
        if (StringUtils.isBlank(esParticipantsIndex)) {
            throw new IllegalArgumentException("ES participants index cannot be empty");
        }
        if (to <= 0) {
            throw new IllegalArgumentException("incorrect from/to range");
        }
        SearchResponse response;
        try {
            int scrollSize = to - from;
            SearchRequest searchRequest = new SearchRequest(esParticipantsIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
            searchSourceBuilder.sort(sortBy);
            searchSourceBuilder.size(scrollSize);
            searchSourceBuilder.from(from);
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got {} participants from ES for instance {} (getParticipantsWithinRange)",
                esParticipants.size(), esParticipantsIndex);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits().value);
    }

    /**
     * Searches for a participant by shortId in the given ES index.
     *
     * @param esParticipantsIndex the ES index to search
     * @param shortId the shortId to search for
     * @return an optional containing in form if {@link ElasticSearchParticipantDto} the participant if found, or empty if not found.
     *     throws a DsmInternalError if more than one participant is found.
     */
    public Optional<ElasticSearchParticipantDto> getParticipantByShortId(String esParticipantsIndex, String shortId) {
        if (StringUtils.isBlank(esParticipantsIndex)) {
            throw new IllegalArgumentException("ES participants index cannot be empty");
        }
        SearchResponse response;
        try {
            int scrollSize = 1000;
            SearchRequest searchRequest = new SearchRequest(esParticipantsIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("profile.hruid", shortId));
            searchSourceBuilder.size(scrollSize);
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        if (esParticipants.size() > 1) {
            throw new DsmInternalError("More than one participant found for shortId {} in ES index {}"
                    .formatted(shortId, esParticipantsIndex));
        }
        logger.info("Got {} participants from ES for instance {} with shortId {} ",
                esParticipants.size(), esParticipantsIndex, shortId);
        return Optional.ofNullable(esParticipants.get(0));
    }

    @Override
    public ElasticSearch getParticipantsByIds(String esIndex, List<String> participantIds) {
        if (Objects.isNull(esIndex)) {
            return new ElasticSearch();
        }
        SearchRequest searchRequest = new SearchRequest(Objects.requireNonNull(esIndex));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(getBoolQueryOfParticipantsId(participantIds)).sort(sortBy);
        searchSourceBuilder.size(participantIds.size());
        searchSourceBuilder.from(0);
        searchRequest.source(searchSourceBuilder);
        SearchResponse response;
        try {
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());

        logger.info("Got {} participants from ES for instance {} (getParticipantsByIds)",
                esParticipants.size(), esIndex);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits().value);
    }

    @Override
    public long getParticipantsSize(String esParticipantsIndex) {
        CountRequest countRequest = new CountRequest(Objects.requireNonNull(esParticipantsIndex));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        countRequest.source(searchSourceBuilder);
        CountResponse response;
        try {
            response = ElasticSearchUtil.getClientInstance().count(countRequest, RequestOptions.DEFAULT);
        } catch (IOException ioe) {
            throw new RuntimeException("Couldn't get participants size of ES for instance " + esParticipantsIndex, ioe);
        }
        return response.getCount();
    }

    @Override
    public ElasticSearch getParticipantsByRangeAndFilter(String esParticipantsIndex, int from, int to, AbstractQueryBuilder queryBuilder) {
        return getParticipantsByRangeAndFilter(esParticipantsIndex, from, to, queryBuilder, null);
    }

    public ElasticSearch getParticipantsByRangeAndFilter(String esIndex, int from, int to, AbstractQueryBuilder queryBuilder,
                                                         String instanceName) {
        if (to <= 0) {
            throw new IllegalArgumentException("incorrect from/to range");
        }
        SearchRequest searchRequest = new SearchRequest(esIndex);
        SearchResponse response;
        try {
            int scrollSize = to - from;
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            //just osteo2
            if (StudyPostFilter.NEW_OSTEO_INSTANCE_NAME.equals(instanceName)) {
                queryBuilder = addOsteo2Filter(queryBuilder);
            }
            searchSourceBuilder.query(queryBuilder).sort(sortBy);
            searchSourceBuilder.size(scrollSize);
            searchSourceBuilder.from(from);
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            // this is likely a bad query, so log it
            logger.error("Error getting ES participants for instance %s. Query: %s".formatted(esIndex, searchRequest));
            throw new DsmInternalError("Error getting ES participants for instance " + esIndex, e);
        }
        List<ElasticSearchParticipantDto> participants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got {} participants from ES for instance {} (getParticipantsByRangeAndFilter)",
                participants.size(), esIndex);
        return new ElasticSearch(participants, response.getHits().getTotalHits().value);
    }

    private AbstractQueryBuilder addOsteo2Filter(AbstractQueryBuilder queryBuilder) {
        //just osteo2
        BoolQueryBuilder osteo2QueryBuilder = new BoolQueryBuilder();
        osteo2QueryBuilder.should(osteoVersion2Surveys("CONSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("CONSENT_ASSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("PARENTAL_CONSENT"));
        osteo2QueryBuilder.should(osteoVersion2Surveys("LOVEDONE"));
        ((BoolQueryBuilder) queryBuilder).must(osteo2QueryBuilder);
        return queryBuilder;
    }

    private NestedQueryBuilder osteoVersion2Surveys(String stableId) {
        BoolQueryBuilder queryBuilderConsentV2 = new BoolQueryBuilder();
        queryBuilderConsentV2.must(new MatchQueryBuilder("activities.activityCode", stableId).operator(Operator.AND));
        queryBuilderConsentV2.mustNot(QueryBuilders.matchQuery("activities.activityVersion", "v1").operator(Operator.AND));
        queryBuilderConsentV2.must(new BoolQueryBuilder().must(new ExistsQueryBuilder("activities.completedAt")));
        NestedQueryBuilder expectedNestedQueryConsent = new NestedQueryBuilder("activities", queryBuilderConsentV2, ScoreMode.Avg);
        return expectedNestedQueryConsent;
    }

    @Override
    public ElasticSearch getParticipantsByRangeAndIds(String participantIndexES, int from, int to, List<String> participantIds) {
        SearchRequest searchRequest = new SearchRequest(Objects.requireNonNull(participantIndexES));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(getBoolQueryOfParticipantsId(participantIds)).sort(sortBy);
        searchSourceBuilder.size(to - from);
        searchSourceBuilder.from(from);
        searchRequest.source(searchSourceBuilder);
        SearchResponse response;
        logger.info("Collecting ES data from index " + participantIndexES);
        try {
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + participantIndexES, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got {} participants from ES for instance {} (getParticipantsByRangeAndIds)",
                esParticipants.size(), participantIndexES);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits().value);
    }

    @Override
    public ElasticSearchParticipantDto getParticipantById(String esParticipantsIndex, String id) {
        String type = Util.getQueryTypeFromId(id);
        String participantId = Objects.requireNonNull(id);
        SearchRequest searchRequest = new SearchRequest(Objects.requireNonNull(esParticipantsIndex));
        TermQueryBuilder shortIdQuery = QueryBuilders.termQuery(type, participantId);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(shortIdQuery);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse;
        Map<String, Object> sourceAsMap;
        logger.info("Collecting ES data from index " + esParticipantsIndex);
        try {
            searchResponse = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
            sourceAsMap = searchResponse.getHits().getHits().length > 0 ? searchResponse.getHits().getHits()[0].getSourceAsMap() :
                    new HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participant from ES for instance " + esParticipantsIndex + " by id: " + participantId,
                    e);
        }
        return parseSourceMap(sourceAsMap, id);
    }

    @Override
    public ElasticSearch getAllParticipantsDataByInstanceIndex(String esParticipantsIndex) {
        long participantsSize = getParticipantsSize(Objects.requireNonNull(esParticipantsIndex));
        SearchRequest searchRequest = new SearchRequest(esParticipantsIndex);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery()).sort(sortBy);
        searchSourceBuilder.from(0);
        searchSourceBuilder.size((int) participantsSize);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse;
        logger.info("Collecting ES data from index " + esParticipantsIndex);
        try {
            searchResponse = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> elasticSearchParticipantDtos = parseSourceMaps(searchResponse.getHits().getHits());
        logger.info("Got {} participants from ES for instance {} (getAllParticipantsDataByInstanceIndex)",
                elasticSearchParticipantDtos.size(), esParticipantsIndex);
        return new ElasticSearch(elasticSearchParticipantDtos, searchResponse.getHits().getTotalHits().value);
    }

    private BoolQueryBuilder getBoolQueryOfParticipantsId(List<String> participantIds) {
        Map<Boolean, List<String>> isGuidMap = participantIds.stream().collect(Collectors.partitioningBy(ParticipantUtil::isGuid));
        BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        isGuidMap.forEach((booleanId, idValues) -> boolQuery.should(
                QueryBuilders.termsQuery(booleanId ? ElasticSearchUtil.PROFILE_GUID : ElasticSearchUtil.PROFILE_LEGACYALTPID, idValues)));
        return boolQuery;
    }

    public Map<String, String> getGuidsByLegacyAltPids(String esParticipantsIndex, List<String> legacyAltPids) {
        logger.info("Collecting ES data from index {} for {} legacy participant IDs",
                esParticipantsIndex, legacyAltPids.size());
        SearchResponse response;
        try {
            SearchRequest searchRequest = new SearchRequest(esParticipantsIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(new TermsQueryBuilder(ElasticSearchUtil.PROFILE_LEGACYALTPID, legacyAltPids.toArray()));
            searchSourceBuilder.size(legacyAltPids.size());
            searchSourceBuilder.fetchSource(
                    new String[] {ElasticSearchUtil.PROFILE_LEGACYALTPID, ElasticSearchUtil.PROFILE_GUID, ElasticSearchUtil.PROXIES}, null
            );
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        SearchHit[] records = response.getHits().getHits();
        logger.info("Got {} participants from ES for instance {} (getGuidsByLegacyAltPids)",
                records.length, esParticipantsIndex);
        SearchHitProxy[] searchHitProxies = Arrays.stream(records)
                .map(SearchHitProxy::new)
                .collect(Collectors.toList())
                .toArray(new SearchHitProxy[] {});
        return extractLegacyAltPidGuidPair(searchHitProxies);
    }

    @Override
    public ReplicationResponse.ShardInfo createDocumentById(String index, String docId, Map<String, Object> data) {

        try {
            IndexRequest indexRequest = new IndexRequest(index, "_doc", docId).source(data);
            IndexResponse response = ElasticSearchUtil.getClientInstance().index(indexRequest, RequestOptions.DEFAULT);
            if (isSuccessfull(response.getShardInfo())) {
                logger.info("Document with id: " + docId + " created successfully in index: " + index);
            } else {
                logger.error("Document with id: " + docId + " could not be created in index" + index);
            }
            return response.getShardInfo();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create participant with index: " + index + " and with id: " + docId, e);
        }
    }

    @Override
    public ReplicationResponse.ShardInfo deleteDocumentById(String index, String docId) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest(index, "_doc", docId);
            DeleteResponse deleteResponse = ElasticSearchUtil.getClientInstance().delete(deleteRequest, RequestOptions.DEFAULT);
            if (isSuccessfull(deleteResponse.getShardInfo())) {
                logger.info("Document with id: " + docId + " created successfully in index: " + index);
            } else {
                logger.error("Document with id: " + docId + " could not be created in index" + index);
            }
            return deleteResponse.getShardInfo();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create participant with index: " + index + " and with id: " + docId, e);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getActivityDefinitions(DDPInstance ddpInstance) {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        String index = ddpInstance.getActivityDefinitionIndexES();
        if (StringUtils.isNotBlank(index)) {
            logger.info("Collecting activity definitions from ES");
            try {
                int scrollSize = 1000;

                SearchRequest searchRequest = new SearchRequest(index);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                SearchResponse response = null;
                int i = 0;
                while (response == null || response.getHits().getHits().length != 0) {
                    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                    searchSourceBuilder.size(scrollSize);
                    searchSourceBuilder.from(i * scrollSize);
                    searchRequest.source(searchSourceBuilder);

                    response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
                    addingActivityDefinitionHits(response, esData);
                    i++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Couldn't get activity definition from ES for instance " + ddpInstance.getName(), e);
            }
            logger.info("Got " + esData.size() + " activity definitions from ES for instance " + ddpInstance.getName());
        }
        return esData;
    }

    public static void addingActivityDefinitionHits(@NonNull SearchResponse response, Map<String, Map<String, Object>> esData) {
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            String activityCode = (String) sourceMap.get(ACTIVITY_CODE);
            String activityVersion = (String) sourceMap.get(ACTIVITY_VERSION);
            if (StringUtils.isNotBlank(activityCode)) {
                esData.put(activityCode + "_" + activityVersion, sourceMap);
            } else {
                esData.put(hit.getId(), sourceMap);
            }
        }
    }

    public MultiSearchResponse executeMultiSearch(String esIndex, List<QueryBuilder> queryBuilders) {
        MultiSearchRequest request = new MultiSearchRequest();
        queryBuilders.forEach(queryBuilder -> {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder);
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.source(searchSourceBuilder);
            searchRequest.indices(esIndex);
            request.add(searchRequest);
        });
        MultiSearchResponse result;
        try {
            result = ElasticSearchUtil.getClientInstance().msearch(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Could not perform multi search request for index: " + esIndex, e);
        }
        return result;
    }

    private boolean isSuccessfull(ReplicationResponse.ShardInfo shardInfo) {
        return shardInfo.getSuccessful() > 0;
    }


    Map<String, String> extractLegacyAltPidGuidPair(SearchHitProxy[] records) {
        Set<String> parentsGuids = getParents(records);
        return Arrays.stream(records)
                .map(SearchHitProxy::getSourceAsMap)
                .filter(this::hasProfile)
                .map(this::getProfile)
                .collect(Collectors.toMap(profileMap ->
                        profileMap.get(ElasticSearchUtil.LEGACY_ALT_PID),
                        profileMap -> profileMap.get(ESObjectConstants.GUID),
                        (prevGuid, currGuid) -> {
                            if (isParentGuid(parentsGuids, prevGuid)) {
                                return currGuid;
                            } else {
                                return prevGuid;
                            }
                        }));
    }

    private boolean isParentGuid(Set<String> parentsGuids, String guid) {
        return parentsGuids.contains(guid);
    }

    private Set<String> getParents(SearchHitProxy[] records) {
        return Arrays.stream(records)
                .map(SearchHitProxy::getSourceAsMap)
                .filter(sourceMap -> sourceMap.containsKey(ElasticSearchUtil.PROXIES))
                .flatMap(sourceMap -> ((List<String>) sourceMap.get(ElasticSearchUtil.PROXIES)).stream())
                .collect(Collectors.toSet());
    }

    private Map<String, String> getProfile(Map<String, Object> sourceMap) {
        return (Map<String, String>) sourceMap.get(ElasticSearchUtil.PROFILE);
    }

    private boolean hasProfile(Map<String, Object> sourceMap) {
        return sourceMap.containsKey(ElasticSearchUtil.PROFILE);
    }

}
