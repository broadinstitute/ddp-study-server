package org.broadinstitute.dsm.model.elastic.search;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.sort.Sort;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.index.query.AbstractQueryBuilder;

public interface ElasticSearchable {

    ElasticSearch getParticipantsWithinRange(String esParticipantsIndex, int from, int to, boolean parseParticipantDtos);

    ElasticSearch getParticipantsByIds(String esParticipantsIndex, List<String> participantIds);

    long getParticipantsSize(String esParticipantsIndex);

    ElasticSearch getParticipantsByRangeAndFilter(String esParticipantsIndex, int from, int to, AbstractQueryBuilder queryBuilder, boolean parseParticipantDtos);

    ElasticSearch getParticipantsByRangeAndIds(String participantIndexES, int from, int to, List<String> participantIds);

    ElasticSearchParticipantDto getParticipantById(String esParticipantsIndex, String id);

    ElasticSearch getAllParticipantsDataByInstanceIndex(String esParticipantsIndex);

    default void setDeserializer(Deserializer deserializer) {

    }

    default void setSortBy(Sort sort) {

    }

    default Map<String, String> getGuidsByLegacyAltPids(String esParticipantsIndex, List<String> legacyAltPids) {
        return Map.of();
    }

    default ReplicationResponse.ShardInfo createDocumentById(String index, String docId, Map<String, Object> data) {
        return new ReplicationResponse.ShardInfo();
    }

    default ReplicationResponse.ShardInfo deleteDocumentById(String index, String docId) {
        return new ReplicationResponse.ShardInfo();
    }

}
