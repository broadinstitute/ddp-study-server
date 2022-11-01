package org.broadinstitute.dsm.model.filter.postfilter;

import java.util.Optional;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.postfilter.osteo.NewOsteoPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.osteo.OldOsteoPostFilter;

public interface StudyPostFilter {

    String OLD_OSTEO_INSTANCE_NAME = "Osteo";
    public static String NEW_OSTEO_INSTANCE_NAME = "osteo2";

    static Optional<StudyPostFilter> fromPayload(StudyPostFilterPayload studyPostFilterPayload) {
        Optional<StudyPostFilter> studyPostFilter = Optional.empty();
        DDPInstanceDto ddpInstanceDto = studyPostFilterPayload.getDdpInstanceDto();
        if (isNewOsteoInstance(ddpInstanceDto)) {
            studyPostFilter = Optional.of(NewOsteoPostFilter.of(studyPostFilterPayload.getElasticSearchParticipantDto(), ddpInstanceDto));
        } else if (isOldOsteoInstance(ddpInstanceDto)) {
            studyPostFilter = Optional.of(OldOsteoPostFilter.of(studyPostFilterPayload.getElasticSearchParticipantDto(), ddpInstanceDto));
        }
        return studyPostFilter;
    }

    private static boolean isOldOsteoInstance(DDPInstanceDto ddpInstanceDto) {
        return OLD_OSTEO_INSTANCE_NAME.equals(ddpInstanceDto.getInstanceName());
    }

    private static boolean isNewOsteoInstance(DDPInstanceDto ddpInstanceDto) {
        return NEW_OSTEO_INSTANCE_NAME.equals(ddpInstanceDto.getInstanceName());
    }

    void filter();

}
