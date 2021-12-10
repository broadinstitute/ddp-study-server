package org.broadinstitute.dsm.model.participant;

import java.util.Collections;
import java.util.List;

import lombok.Getter;

@Getter
public class ParticipantWrapperResult {

    private final long totalCount;
    private final List<ParticipantWrapperDto> participants;

    public ParticipantWrapperResult(long totalCount, List<ParticipantWrapperDto> participants) {
        this.totalCount = totalCount;
        this.participants = participants;
    }

    public ParticipantWrapperResult() {
        this(0, Collections.emptyList());
    }


}
