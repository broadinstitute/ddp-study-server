package org.broadinstitute.dsm.model.elastic.search;

import lombok.Getter;

import java.util.Map;

public class UnparsedESParticipantDto extends ElasticSearchParticipantDto {
    @Getter
    private final Map<String, Object> dataAsMap;

    public UnparsedESParticipantDto(Map<String, Object> dataAsMap) {
        super();
        this.dataAsMap = dataAsMap;
    }

}
