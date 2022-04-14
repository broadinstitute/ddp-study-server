package org.broadinstitute.dsm.model.elastic.search;

import java.util.Map;
import java.util.Optional;

public interface Deserializer {
    Optional<ElasticSearchParticipantDto> deserialize(Map<String, Object> sourceMap);
}
