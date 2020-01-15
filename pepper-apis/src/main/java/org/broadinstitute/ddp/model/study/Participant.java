package org.broadinstitute.ddp.model.study;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.user.User;

/**
 * Represents a user who is participating in a study. Includes system-wide user data, as well as study-specific data like medical providers
 * and activity responses.
 */
public class Participant {

    private User user;
    private Map<String, List<ActivityResponse>> responses;

    // todo: better models for status and medical providers
    private EnrollmentStatusDto status;
    private List<MedicalProviderDto> providers;
    private AgeOfMajorityRule ageOfMajorityRule;

    public Participant(EnrollmentStatusDto status, User user) {
        this.status = status;
        this.user = user;
        this.providers = new ArrayList<>();
        this.responses = new HashMap<>();
    }

    public EnrollmentStatusDto getStatus() {
        return status;
    }

    public User getUser() {
        return user;
    }

    public List<MedicalProviderDto> getProviders() {
        return providers;
    }

    public void addProvider(MedicalProviderDto providerDto) {
        providers.add(providerDto);
    }

    public void addAOMRule(AgeOfMajorityRule ageOfMajorityRule) {
        this.ageOfMajorityRule = ageOfMajorityRule;
    }

    public List<ActivityResponse> getAllResponses() {
        return responses.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public void clearAllResponses() {
        responses.clear();
    }

    public List<ActivityResponse> getResponses(String activityTag) {
        return responses.computeIfAbsent(activityTag, tag -> new ArrayList<>());
    }

    public void addResponse(ActivityResponse response) {
        responses.computeIfAbsent(response.getActivityTag(), tag -> new ArrayList<>()).add(response);
    }

    public AgeOfMajorityRule getAgeOfMajorityRule() {
        return ageOfMajorityRule;
    }
}
