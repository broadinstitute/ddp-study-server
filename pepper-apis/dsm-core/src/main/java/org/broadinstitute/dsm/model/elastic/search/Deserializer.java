package org.broadinstitute.dsm.model.elastic.search;

import java.util.Map;
import java.util.Optional;

public interface Deserializer {
    // TODO: unclear why this returns an Optional, since no implementation actually returns an empty Optional. -DC
    Optional<ElasticSearchParticipantDto> deserialize(Map<String, Object> sourceMap);
}
