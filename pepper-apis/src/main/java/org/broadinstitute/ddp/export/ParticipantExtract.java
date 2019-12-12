package org.broadinstitute.ddp.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * A data extract that encapsulates all the data for a participant that is relevant to a study.
 */
public class ParticipantExtract {

    private EnrollmentStatusDto statusDto;
    private UserDto userDto;
    private UserProfileDto profileDto;
    private String userEmail;
    private MailAddress address;
    private List<MedicalProviderDto> providers;
    private List<ActivityInstance> instances;
    private Map<String, List<ActivityInstanceStatusDto>> instanceStatuses;

    public ParticipantExtract(EnrollmentStatusDto statusDto, UserDto userDto, UserProfileDto profileDto, MailAddress address) {
        this(statusDto, userDto, null, profileDto, address, new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    }

    public ParticipantExtract(EnrollmentStatusDto statusDto, UserDto userDto, String userEmail, UserProfileDto profileDto,
                              MailAddress address, List<MedicalProviderDto> providers,
                              List<ActivityInstance> instances,
                              Map<String, List<ActivityInstanceStatusDto>> instanceStatuses) {
        this.statusDto = statusDto;
        this.userDto = userDto;
        this.userEmail = userEmail;
        this.profileDto = profileDto;
        this.address = address;
        this.providers = providers;
        this.instances = instances;
        this.instanceStatuses = instanceStatuses;
    }

    public long getUserId() {
        return userDto.getUserId();
    }

    public String getUserGuid() {
        return userDto.getUserGuid();
    }

    public EnrollmentStatusType getEnrollmentStatus() {
        return statusDto.getEnrollmentStatus();
    }

    public EnrollmentStatusDto getStatusDto() {
        return statusDto;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public UserProfileDto getProfileDto() {
        return profileDto;
    }

    public MailAddress getAddress() {
        return address;
    }

    public List<MedicalProviderDto> getProviders() {
        return providers;
    }

    public List<ActivityInstance> getInstances() {
        return instances;
    }

    public Map<String, List<ActivityInstanceStatusDto>> getInstanceStatuses() {
        return instanceStatuses;
    }

    public boolean hasProfile() {
        return profileDto != null;
    }

    public boolean hasAddress() {
        return address != null;
    }

    public ActivityInstance findInstanceForActivityVersion(String activityCode, long revStart, Long revEnd) {
        return instances.stream()
                .filter(inst -> activityCode.equals(inst.getActivityCode())
                        && revStart <= inst.getCreatedAtMillis()
                        && (revEnd == null || inst.getCreatedAtMillis() < revEnd))
                .findFirst()
                .orElse(null);
    }

    public List<ActivityInstanceStatusDto> findInstanceStatuses(String instanceGuid) {
        return instanceStatuses.get(instanceGuid);
    }
}
