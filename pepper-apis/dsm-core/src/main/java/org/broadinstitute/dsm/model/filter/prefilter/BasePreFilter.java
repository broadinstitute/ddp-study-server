package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

public abstract class BasePreFilter implements PreFilter {

    protected final ElasticSearchParticipantDto elasticSearchParticipantDto;
    protected final DDPInstanceDto ddpInstanceDto;

    protected BasePreFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        this.elasticSearchParticipantDto = elasticSearchParticipantDto;
        this.ddpInstanceDto = ddpInstanceDto;
    }

    public ElasticSearchParticipantDto getElasticSearchParticipantDto() {
        return elasticSearchParticipantDto;
    }

    public DDPInstanceDto getDdpInstanceDto() {
        return ddpInstanceDto;
    }
}