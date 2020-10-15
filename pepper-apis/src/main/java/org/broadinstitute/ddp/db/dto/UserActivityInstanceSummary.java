package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.model.user.User;

public class UserActivityInstanceSummary {
    private User participantUser;
    private List<ActivityInstanceDto> activityInstanceDtos;

    public UserActivityInstanceSummary(User participantUser, List<ActivityInstanceDto> dtos) {
        this.participantUser = participantUser;
        this.activityInstanceDtos = new ArrayList<>(dtos);
    }

    public User getParticipantUser() {
        return participantUser;
    }

    public Optional<ActivityInstanceDto> getActivityInstanceByGuid(String guid) {
        return activityInstanceDtos.stream().filter(ai -> ai.getGuid().equals(guid)).findFirst();
    }

    public Optional<ActivityInstanceDto> getLatestActivityInstance(String activityCode) {
        return activityInstanceDtos.stream()
                .filter(ai -> ai.getActivityCode().equals(activityCode))
                .sorted((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()))
                .findFirst();
    }

}
