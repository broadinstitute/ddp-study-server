package org.broadinstitute.dsm.model.elastic.search;

import java.util.Map;
import java.util.Optional;

/**
 * returns unparsed participantDTO objects, useful when performance concerns make the robust parsing operations
 * undesirable
 */
public class UnparsedDeserializer implements Deserializer {
    public Optional<ElasticSearchParticipantDto> deserialize(Map<String, Object> sourceMap) {
        return Optional.of(new UnparsedESParticipantDto(sourceMap));
    }
}
