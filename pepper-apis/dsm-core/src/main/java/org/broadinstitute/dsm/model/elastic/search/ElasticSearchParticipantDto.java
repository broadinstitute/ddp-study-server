package org.broadinstitute.dsm.model.elastic.search;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Address;
import org.broadinstitute.dsm.model.elastic.ESComputed;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.Files;

@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticSearchParticipantDto {

    private Address address;
    private List<Object> medicalProviders;
    private List<Object> invitations;
    private List<Activities> activities;
    private List<String> governedUsers;
    private ESComputed computed;
    private Long statusTimestamp;
    private Profile profile;
    private List<Files> files;
    private List<String> proxies;
    private List<Map<String, Object>> workflows;
    private String status;
    private Dsm dsm;
    private String queriedParticipantId; // optional, used for troubleshooting to report
    // the participant id that was queried
    @Getter
    private String ddp;

    private ElasticSearchParticipantDto(ElasticSearchParticipantDto.Builder builder) {
        this.address = builder.address;
        this.medicalProviders = builder.medicalProviders;
        this.invitations = builder.invitations;
        this.activities = builder.activities;
        this.statusTimestamp = builder.statusTimeStamp;
        this.profile = builder.profile;
        this.files = builder.files;
        this.proxies = builder.proxies;
        this.workflows = builder.workflows;
        this.status = builder.status;
        this.dsm = builder.dsm;
        this.computed = builder.computed;
        this.governedUsers = builder.governedUsers;
        this.queriedParticipantId = builder.queriedParticipantId;
    }

    protected ElasticSearchParticipantDto() {  }

    public Optional<Address> getAddress() {
        return Optional.ofNullable(address);
    }

    public List<Object> getMedicalProviders() {
        return medicalProviders == null ? Collections.emptyList() : medicalProviders;
    }

    public String getQueriedParticipantId() {
        return queriedParticipantId;
    }

    public List<Object> getInvitations() {
        return invitations == null ? Collections.emptyList() : invitations;
    }

    public List<Activities> getActivities() {
        return activities == null ? Collections.emptyList() : activities;
    }

    public Optional<Long> getStatusTimestamp() {
        return Optional.ofNullable(statusTimestamp);
    }

    public Optional<Profile> getProfile() {
        return Optional.ofNullable(profile);
    }

    public List<Files> getFiles() {
        return files == null ? Collections.emptyList() : files;
    }

    public List<String> getProxies() {
        return proxies == null ? Collections.emptyList() : proxies;
    }

    public List<Map<String, Object>> getWorkflows() {
        return workflows == null ? Collections.emptyList() : workflows;
    }

    public Optional<String> getStatus() {
        return Optional.ofNullable(status);
    }

    public Optional<Dsm> getDsm() {
        return Optional.ofNullable(dsm);
    }

    @JsonGetter("dsm")
    private Dsm dsm() {
        return dsm;
    }

    public Optional<ESComputed> getComputed() {
        return Optional.ofNullable(computed);
    }

    public List<String> getGovernedUsers() {
        return governedUsers == null ? Collections.emptyList() : governedUsers;
    }


    /**
     * Returns the participant id by checking the profile
     * for guid and altpid, preferentially returning the guid.
     */
    public String getParticipantId() {
        return getProfile().map(esProfile -> StringUtils.isNotBlank(esProfile.getGuid())
                        ? esProfile.getGuid()
                        : esProfile.getLegacyAltPid())
                .orElse(StringUtils.EMPTY);
    }

    public static class Builder {
        private Address address;
        private List<Object> medicalProviders;
        private List<Object> invitations;
        private ESComputed computed;
        private List<Activities> activities;
        private List<String> governedUsers;
        private Long statusTimeStamp;
        private Profile profile;
        private List<Files> files;
        private List<String> proxies;
        private List<Map<String, Object>> workflows;
        private String status;
        private Dsm dsm;
        private String queriedParticipantId;

        public Builder() {
        }

        public Builder withAddress(Address address) {
            this.address = address;
            return this;
        }

        public Builder withMedicalProviders(List<Object> medicalProviders) {
            this.medicalProviders = medicalProviders;
            return this;
        }

        public Builder withInvitations(List<Object> invitations) {
            this.invitations = invitations;
            return this;
        }

        public Builder withActivities(List<Activities> activities) {
            this.activities = activities;
            return this;
        }

        /**
         * Sets the queried participant id.  Used only for troubleshooting.
         */
        public Builder withQueriedParticipantId(String queriedParticipantId) {
            this.queriedParticipantId = queriedParticipantId;
            return this;
        }

        public Builder withStatusTimeStamp(Long statusTimeStamp) {
            this.statusTimeStamp = statusTimeStamp;
            return this;
        }

        public Builder withProfile(Profile profile) {
            this.profile = profile;
            return this;
        }

        public Builder withFiles(List<Files> files) {
            this.files = files;
            return this;
        }

        public Builder withProxies(List<String> proxies) {
            this.proxies = proxies;
            return this;
        }

        public Builder withWorkFlows(List<Map<String, Object>> workflows) {
            this.workflows = workflows;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder withDsm(Dsm dsm) {
            this.dsm = dsm;
            return this;
        }

        public Builder withComputed(ESComputed computed) {
            this.computed = computed;
            return this;
        }

        public Builder withGovernedUsers(List<String> governedUsers) {
            this.governedUsers = governedUsers;
            return this;
        }

        public ElasticSearchParticipantDto build() {
            return new ElasticSearchParticipantDto(this);
        }
    }


}
