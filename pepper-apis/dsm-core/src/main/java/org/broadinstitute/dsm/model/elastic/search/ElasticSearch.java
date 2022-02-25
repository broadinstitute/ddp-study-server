package org.broadinstitute.dsm.model.elastic.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.sort.CustomSortBuilder;
import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.*;
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
        this.sortBy = SortBuilders.fieldSort(ElasticSearchUtil.PROFILE_CREATED_AT).order(SortOrder.ASC);
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

    public Optional<ElasticSearchParticipantDto> parseSourceMap(Map<String, Object> sourceMap) {
        if (sourceMap == null) return Optional.of(new ElasticSearchParticipantDto.Builder().build());
        Optional<ElasticSearchParticipantDto> deserializedSourceMap = deserializer.deserialize(sourceMap);
        return deserializedSourceMap.isPresent()
                ? deserializedSourceMap
                : Optional.of(new ElasticSearchParticipantDto.Builder().build());
    }

    public List<ElasticSearchParticipantDto> parseSourceMaps(SearchHit[] searchHits) {
        if (Objects.isNull(searchHits)) return Collections.emptyList();
        List<ElasticSearchParticipantDto> result = new ArrayList<>();
        String ddp = getDdpFromSearchHit(Arrays.stream(searchHits).findFirst().orElse(null));
        for (SearchHit searchHit: searchHits) {
            Optional<ElasticSearchParticipantDto> maybeElasticSearchResult = parseSourceMap(searchHit.getSourceAsMap());
            maybeElasticSearchResult.ifPresent(elasticSearchParticipantDto -> {
                elasticSearchParticipantDto.setDdp(ddp);
                result.add(elasticSearchParticipantDto);
            });
        }
        return result;
    }

    private String getDdpFromSearchHit(SearchHit searchHit) {
        if (Objects.isNull(searchHit)) return "";
        return getDdpFromIndex(searchHit.getIndex());
    }

    String getDdpFromIndex(String searchHitIndex) {
        if (StringUtils.isBlank(searchHitIndex)) return "";
        int dotIndex = searchHitIndex.lastIndexOf('.');
        return searchHitIndex.substring(dotIndex + 1);
    }

    @Override
    public ElasticSearch getParticipantsWithinRange(String esParticipantsIndex, int from, int to) {
        if (StringUtils.isBlank(esParticipantsIndex)) throw new IllegalArgumentException("ES participants index cannot be empty");
        if (to <= 0) throw new IllegalArgumentException("incorrect from/to range");
        logger.info("Collecting ES data");
        SearchResponse response;
        try {
            int scrollSize = to - from;
            SearchRequest searchRequest = new SearchRequest(esParticipantsIndex);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchAllQuery()).sort(sortBy);
            searchSourceBuilder.size(scrollSize);
            searchSourceBuilder.from(from);
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got " + esParticipants.size() + " participants from ES for instance " + esParticipantsIndex);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits());
    }

    @Override
    public ElasticSearch getParticipantsByIds(String esIndex, List<String> participantIds) {
        if (Objects.isNull(esIndex)) return new ElasticSearch();
        SearchRequest searchRequest = new SearchRequest(Objects.requireNonNull(esIndex));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(getBoolQueryOfParticipantsId(participantIds)).sort(sortBy);
        searchSourceBuilder.size(participantIds.size());
        searchSourceBuilder.from(0);
        searchRequest.source(searchSourceBuilder);
        SearchResponse response;
        logger.info("Collecting ES data");
        try {
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got " + esParticipants.size() + " participants from ES for instance " + esIndex);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits());
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
        if (to <= 0) throw new IllegalArgumentException("incorrect from/to range");
        logger.info("Collecting ES data");
        SearchResponse response;
        try {
            int scrollSize = to - from;
            SearchRequest searchRequest = new SearchRequest(Objects.requireNonNull(esParticipantsIndex));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder).sort(sortBy);
            searchSourceBuilder.size(scrollSize);
            searchSourceBuilder.from(from);
            searchRequest.source(searchSourceBuilder);
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        }
        catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got " + esParticipants.size() + " participants from ES for instance " + esParticipantsIndex);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits());
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
        logger.info("Collecting ES data");
        try {
            response = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + participantIndexES, e);
        }
        List<ElasticSearchParticipantDto> esParticipants = parseSourceMaps(response.getHits().getHits());
        logger.info("Got " + esParticipants.size() + " participants from ES for instance " + participantIndexES);
        return new ElasticSearch(esParticipants, response.getHits().getTotalHits());
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
        logger.info("Collecting ES data");
        try {
            searchResponse = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
            sourceAsMap = searchResponse.getHits().getHits().length > 0 ?
                    searchResponse.getHits().getHits()[0].getSourceAsMap() : new HashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participant from ES for instance " + esParticipantsIndex + " by id: " + participantId, e);
        }
        return parseSourceMap(sourceAsMap).orElseThrow();
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
        logger.info("Collecting ES data");
        try {
            searchResponse = ElasticSearchUtil.getClientInstance().search(searchRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get participants from ES for instance " + esParticipantsIndex, e);
        }
        List<ElasticSearchParticipantDto> elasticSearchParticipantDtos = parseSourceMaps(searchResponse.getHits().getHits());
        logger.info("Got " + elasticSearchParticipantDtos.size() + " participants from ES for instance " + esParticipantsIndex);
        return new ElasticSearch(elasticSearchParticipantDtos, searchResponse.getHits().getTotalHits());
    }

    private BoolQueryBuilder getBoolQueryOfParticipantsId(List<String> participantIds) {
        Map<Boolean, List<String>> isGuidMap = participantIds.stream().collect(Collectors.partitioningBy(ParticipantUtil::isGuid));
        BoolQueryBuilder boolQuery = new BoolQueryBuilder();
        isGuidMap.forEach((booleanId, idValues) -> boolQuery.should(QueryBuilders.termsQuery(booleanId
                ? ElasticSearchUtil.PROFILE_GUID
                : ElasticSearchUtil.PROFILE_LEGACYALTPID, idValues)));
        return boolQuery;
    }

}
