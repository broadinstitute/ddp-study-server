package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.NewOsteoPreFilter;

import java.util.Optional;

public interface PreFilter {

    String NEW_OSTEO_INSTANCE_NAME = "osteo2";

    static Optional<PreFilter> fromPayload(PreFilterPayload payload) {
        DDPInstanceDto ddpInstanceDto = payload.getDdpInstanceDto();
        if (NEW_OSTEO_INSTANCE_NAME.equals(ddpInstanceDto.getInstanceName())) {
            return Optional.of(NewOsteoPreFilter.of(payload.getElasticSearchParticipantDto(), ddpInstanceDto));
        }
        return Optional.empty();
    }

    void filter();

}
