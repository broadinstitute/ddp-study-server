package org.broadinstitute.dsm.model.filter.postfilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

public class StudyPostFilterPayload {

    private final ElasticSearchParticipantDto elasticSearchParticipantDto;
    private final DDPInstanceDto ddpInstanceDto;

    private StudyPostFilterPayload(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        this.elasticSearchParticipantDto = elasticSearchParticipantDto;
        this.ddpInstanceDto = ddpInstanceDto;
    }

    public ElasticSearchParticipantDto getElasticSearchParticipantDto() {
        return elasticSearchParticipantDto;
    }

    public DDPInstanceDto getDdpInstanceDto() {
        return ddpInstanceDto;
    }

    public static StudyPostFilterPayload of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new StudyPostFilterPayload(elasticSearchParticipantDto, ddpInstanceDto);
    }
}
