
package org.broadinstitute.dsm.model.filter.postfilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;

public abstract class BaseStudyPostFilter implements StudyPostFilter {

    protected final ElasticSearchParticipantDto elasticSearchParticipantDto;
    protected final DDPInstanceDto ddpInstanceDto;

    protected BaseStudyPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
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
