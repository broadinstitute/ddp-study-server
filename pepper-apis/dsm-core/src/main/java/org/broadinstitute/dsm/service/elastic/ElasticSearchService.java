package org.broadinstitute.dsm.service.elastic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.ObjectTransformer;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
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

    public ElasticSearchService() {
        this.elasticSearch = new ElasticSearch();
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
     * Return a list of all participant guids in a participant ES index
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
                if (StringUtils.isNotBlank(profile.getGuid()) && StringUtils.isNotBlank(profile.getLegacyAltPid())) {
                    guidToPid.put(profile.getGuid(), profile.getLegacyAltPid());
                }
            }
        });
        return guidToPid;
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
