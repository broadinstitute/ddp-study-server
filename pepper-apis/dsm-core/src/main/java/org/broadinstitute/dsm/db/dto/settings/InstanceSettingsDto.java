package org.broadinstitute.dsm.db.dto.settings;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.model.Value;

public class InstanceSettingsDto {

    private int instanceSettingsId;
    private int ddpInstanceId;
    private List<Value> mrCoverPdf;
    private List<Value> kitBehaviorChange;
    private List<Value> specialFormat;
    private List<Value> hideESFields;
    private boolean hideSamplesTab;
    private List<Value> studySpecificStatuses;
    private List<Value> defaultColumns;
    private boolean hasInvitations;
    private boolean gbfShippedTriggerDSSDelivered;
    private boolean hasAddressTab;
    private boolean hasComputedObject;

    public Optional<Integer> getInstanceSettingsId() {
        return Optional.of(instanceSettingsId);
    }

    public Optional<Integer> getDdpInstanceId() {
        return Optional.of(ddpInstanceId);
    }

    public Optional<List<Value>> getMrCoverPdf() {
        return Optional.ofNullable(mrCoverPdf);
    }

    public Optional<List<Value>> getKitBehaviorChange() {
        return Optional.ofNullable(kitBehaviorChange);
    }

    public Optional<List<Value>> getSpecialFormat() {
        return Optional.ofNullable(specialFormat);
    }

    public Optional<List<Value>> getHideESFields() {
        return Optional.ofNullable(hideESFields);
    }

    public Optional<Boolean> isHideSamplesTab() {
        return Optional.of(hideSamplesTab);
    }

    public Optional<List<Value>> getStudySpecificStatuses() {
        return Optional.ofNullable(studySpecificStatuses);
    }

    public Optional<List<Value>> getDefaultColumns() {
        return Optional.ofNullable(defaultColumns);
    }

    public Optional<Boolean> isHasInvitations() {
        return Optional.of(hasInvitations);
    }

    public Optional<Boolean> isGbfShippedTriggerDSSDelivered() {
        return Optional.of(gbfShippedTriggerDSSDelivered);
    }

    public Optional<Boolean> hasAddressTab() {
        return Optional.of(hasAddressTab);
    }

    public Optional<Boolean> hasComputedObject() {
        return Optional.of(hasComputedObject);
    }


    private InstanceSettingsDto(Builder builder) {
        this.instanceSettingsId = builder.instanceSettingsId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.mrCoverPdf = builder.mrCoverPdf;
        this.kitBehaviorChange = builder.kitBehaviorChange;
        this.specialFormat = builder.specialFormat;
        this.hideESFields = builder.hideESFields;
        this.hideSamplesTab = builder.hideSamplesTab;
        this.studySpecificStatuses = builder.studySpecificStatuses;
        this.defaultColumns = builder.defaultColumns;
        this.hasInvitations = builder.hasInvitations;
        this.gbfShippedTriggerDSSDelivered = builder.gbfShippedTriggerDSSDelivered;
        this.hasAddressTab = builder.hasAddressTab;
        this.hasComputedObject = builder.hasComputedObject;
    }


    public static class Builder {
        public int instanceSettingsId;
        public int ddpInstanceId;
        public List<Value> mrCoverPdf;
        public List<Value> kitBehaviorChange;
        public List<Value> specialFormat;
        public List<Value> hideESFields;
        public boolean hideSamplesTab;
        public List<Value> studySpecificStatuses;
        public List<Value> defaultColumns;
        public boolean hasInvitations;
        public boolean gbfShippedTriggerDSSDelivered;
        public boolean hasAddressTab;
        public boolean hasComputedObject;

        public Builder withInstanceSettingsId(int instanceSettingsId) {
            this.instanceSettingsId = instanceSettingsId;
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withMrCoverPdf(List<Value> mrCoverPdf) {
            this.mrCoverPdf = mrCoverPdf;
            return this;
        }

        public Builder withKitBehaviorChange(List<Value> kitBehaviorChange) {
            this.kitBehaviorChange = kitBehaviorChange;
            return this;
        }

        public Builder withSpecialFormat(List<Value> specialFormat) {
            this.specialFormat = specialFormat;
            return this;
        }

        public Builder withHideEsFields(List<Value> hideESFields) {
            this.hideESFields = hideESFields;
            return this;
        }

        public Builder withHideSamplesTab(boolean hideSamplesTab) {
            this.hideSamplesTab = hideSamplesTab;
            return this;
        }

        public Builder withStudySpecificStatuses(List<Value> studySpecificStatuses) {
            this.studySpecificStatuses = studySpecificStatuses;
            return this;
        }

        public Builder withDefaultColumns(List<Value> defaultColumns) {
            this.defaultColumns = defaultColumns;
            return this;
        }

        public Builder withHasInvitations(boolean hasInvitations) {
            this.hasInvitations = hasInvitations;
            return this;
        }

        public Builder withGbfShippedTriggerDssDelivered(boolean gbfShippedTriggerDSSDelivered) {
            this.gbfShippedTriggerDSSDelivered = gbfShippedTriggerDSSDelivered;
            return this;
        }

        public Builder withHasAddressTab(boolean hasAddressTab) {
            this.hasAddressTab = hasAddressTab;
            return this;
        }

        public Builder withHasComputedObject(boolean hasComputedObject) {
            this.hasComputedObject = hasComputedObject;
            return this;
        }

        public InstanceSettingsDto build() {
            return new InstanceSettingsDto(this);
        }
    }
}
