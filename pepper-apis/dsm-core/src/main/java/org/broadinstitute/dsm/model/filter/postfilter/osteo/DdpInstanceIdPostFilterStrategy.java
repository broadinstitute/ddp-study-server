package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;

/**
 * A class that implements the StudyPostFilterStrategy by checking if a document has a specific Instance ID value.
 */
public class DdpInstanceIdPostFilterStrategy implements StudyPostFilterStrategy<HasDdpInstanceId> {

    private final DDPInstanceDto ddpInstanceDto;

    public DdpInstanceIdPostFilterStrategy(DDPInstanceDto ddpInstanceDto) {
        this.ddpInstanceDto = ddpInstanceDto;
    }

    @Override
    public boolean test(HasDdpInstanceId hasDdpInstanceId) {
        return hasDdpInstanceId.extractDdpInstanceId()
                .filter(instanceId -> instanceId.longValue() == ddpInstanceDto.getDdpInstanceId())
                .isPresent();
    }
}
