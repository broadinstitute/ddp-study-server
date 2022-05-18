package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

public class PreFilterPayload {

    private final ElasticSearchParticipantDto elasticSearchParticipantDto;
    private final DDPInstanceDto ddpInstanceDto;

    private PreFilterPayload(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        this.elasticSearchParticipantDto = elasticSearchParticipantDto;
        this.ddpInstanceDto = ddpInstanceDto;
    }

    public ElasticSearchParticipantDto getElasticSearchParticipantDto() {
        return elasticSearchParticipantDto;
    }

    public DDPInstanceDto getDdpInstanceDto() {
        return ddpInstanceDto;
    }

    public static PreFilterPayload of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new PreFilterPayload(elasticSearchParticipantDto, ddpInstanceDto);
    }
}
