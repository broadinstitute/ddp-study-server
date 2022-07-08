package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

public class StudyPreFilterPayload {

    private final ElasticSearchParticipantDto elasticSearchParticipantDto;
    private final DDPInstanceDto ddpInstanceDto;

    private StudyPreFilterPayload(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        this.elasticSearchParticipantDto = elasticSearchParticipantDto;
        this.ddpInstanceDto = ddpInstanceDto;
    }

    public ElasticSearchParticipantDto getElasticSearchParticipantDto() {
        return elasticSearchParticipantDto;
    }

    public DDPInstanceDto getDdpInstanceDto() {
        return ddpInstanceDto;
    }

    public static StudyPreFilterPayload of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new StudyPreFilterPayload(elasticSearchParticipantDto, ddpInstanceDto);
    }
}
