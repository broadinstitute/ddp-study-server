package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class UserActivityInstanceSummary {
    private final User participantUser;
    private final List<ActivityInstanceDto> activityInstanceDtos;

    public UserActivityInstanceSummary(final User participantUser, final List<ActivityInstanceDto> dtos) {
        this.participantUser = participantUser;
        this.activityInstanceDtos = new ArrayList<>(dtos);
    }

    public User getParticipantUser() {
        return participantUser;
    }

    public Stream<ActivityInstanceDto> getInstancesStream() {
        return activityInstanceDtos.stream();
    }

    public Optional<ActivityInstanceDto> getActivityInstanceByGuid(String guid) {
        return activityInstanceDtos.stream().filter(ai -> ai.getGuid().equals(guid)).findFirst();
    }

    public Optional<ActivityInstanceDto> getLatestActivityInstance(String activityCode) {
        return activityInstanceDtos.stream()
                .filter(ai -> ai.getActivityCode().equals(activityCode))
                .min((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
    }

}
