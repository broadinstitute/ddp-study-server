package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserActivityInstanceSummary {
    private List<ActivityInstanceDto> activityInstanceDtos;

    public UserActivityInstanceSummary(List<ActivityInstanceDto> activityInstanceDtos) {
        this.activityInstanceDtos = new ArrayList<>(activityInstanceDtos);
    }

    public Optional<ActivityInstanceDto> getActvityInstanceByGuid(String guid) {
        return activityInstanceDtos.stream().filter(ai -> ai.getGuid().equals(guid)).findFirst();
    }

    public Optional<ActivityInstanceDto> getLatestActivityInstance(String activityCode) {
        return activityInstanceDtos.stream()
                .filter(ai -> ai.getActivityCode().equals(activityCode))
                .sorted((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()))
                .findFirst();
    }

}
