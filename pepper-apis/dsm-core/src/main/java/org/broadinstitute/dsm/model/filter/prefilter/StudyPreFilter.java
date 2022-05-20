package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.NewOsteoPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.OldOsteoPreFilter;

import java.util.Optional;

public interface StudyPreFilter {

    String OLD_OSTEO_INSTANCE_NAME = "Osteo";
    String NEW_OSTEO_INSTANCE_NAME = "osteo2";

    static Optional<StudyPreFilter> fromPayload(StudyPreFilterPayload studyPreFilterPayload) {
        Optional<StudyPreFilter> studyPreFilter = Optional.empty();
        DDPInstanceDto ddpInstanceDto = studyPreFilterPayload.getDdpInstanceDto();
        if (isNewOsteoInstance(ddpInstanceDto)) {
            studyPreFilter = Optional.of(NewOsteoPreFilter.of(studyPreFilterPayload.getElasticSearchParticipantDto(), ddpInstanceDto));
        } else if (isOldOsteoInstance(ddpInstanceDto)) {
            studyPreFilter = Optional.of(OldOsteoPreFilter.of(studyPreFilterPayload.getElasticSearchParticipantDto(), ddpInstanceDto));
        }
        return studyPreFilter;
    }

    private static boolean isOldOsteoInstance(DDPInstanceDto ddpInstanceDto) {
        return OLD_OSTEO_INSTANCE_NAME.equals(ddpInstanceDto.getInstanceName());
    }

    private static boolean isNewOsteoInstance(DDPInstanceDto ddpInstanceDto) {
        return NEW_OSTEO_INSTANCE_NAME.equals(ddpInstanceDto.getInstanceName());
    }

    void filter();

}
