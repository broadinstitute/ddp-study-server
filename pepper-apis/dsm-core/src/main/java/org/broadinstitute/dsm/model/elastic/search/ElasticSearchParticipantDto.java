package org.broadinstitute.dsm.model.elastic.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.ESAddress;
import org.broadinstitute.dsm.model.elastic.ESComputed;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.ESProfile;

@Setter
public class ElasticSearchParticipantDto {

    private ESAddress address;
    private List<Object> medicalProviders;
    private List<Object> invitations;
    private List<ESActivities> activities;
    private ESComputed computed;
    private Long statusTimestamp;
    private ESProfile profile;
    private List<Object> files;
    private List<String> proxies;
    private List<Map<String, Object>> workflows;
    private String status;
    private ESDsm dsm;
    private String ddp;

    private ElasticSearchParticipantDto(Builder builder) {
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
    }

    public Optional<ESAddress> getAddress() {
        return Optional.ofNullable(address);
    }

    public Optional<List<Object>> getMedicalProviders() {
        return Optional.ofNullable(medicalProviders);
    }

    public Optional<List<Object>> getInvitations() {
        return Optional.ofNullable(invitations);
    }

    public Optional<List<ESActivities>> getActivities() {
        return Optional.ofNullable(activities);
    }

    public Optional<Long> getStatusTimestamp() {
        return Optional.ofNullable(statusTimestamp);
    }

    public Optional<ESProfile> getProfile() {
        return Optional.ofNullable(profile);
    }

    public Optional<List<Object>> getFiles() {
        return Optional.ofNullable(files);
    }

    public Optional<List<String>> getProxies() {
        return Optional.ofNullable(proxies);
    }

    public Optional<List<Map<String, Object>>> getWorkflows() {
        return Optional.ofNullable(workflows);
    }

    public Optional<String> getStatus() {
        return Optional.ofNullable(status);
    }

    public Optional<ESDsm> getDsm() {
        return Optional.ofNullable(dsm);
    }

    public Optional<ESComputed> getComputed() {
        return Optional.ofNullable(computed);
    }

    public String getParticipantId() {
        return getProfile().map(esProfile -> StringUtils.isNotBlank(esProfile.getParticipantGuid())
                ? esProfile.getParticipantGuid()
                : esProfile.getParticipantLegacyAltPid())
                .orElse("");
    }

    public static class Builder {
        private ESAddress address;
        private List<Object> medicalProviders;
        private List<Object> invitations;
        private ESComputed computed;
        private List<ESActivities> activities;
        private Long statusTimeStamp;
        private ESProfile profile;
        private List<Object> files;
        private List<String> proxies;
        private List<Map<String, Object>> workflows;
        private String status;
        private ESDsm dsm;

        public Builder() {
        }

        public Builder withAddress(ESAddress esAddress) {
            this.address = esAddress;
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

        public Builder withActivities(List<ESActivities> activities) {
            this.activities = activities;
            return this;
        }

        public Builder withStatusTimeStamp(Long statusTimeStamp) {
            this.statusTimeStamp = statusTimeStamp;
            return this;
        }

        public Builder withProfile(ESProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder withFiles(List<Object> files) {
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

        public Builder withDsm(ESDsm dsm) {
            this.dsm = dsm;
            return this;
        }

        public Builder withComputed(ESComputed computed) {
            this.computed = computed;
            return this;
        }

        public ElasticSearchParticipantDto build() {
            return new ElasticSearchParticipantDto(this);
        }
    }


}
