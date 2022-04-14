package org.broadinstitute.dsm.db.dto.settings;


import lombok.Data;

@Data
public class FieldSettingsDto {

    private int fieldSettingsId;
    private int ddpInstanceId;
    private String fieldType;
    private String columnName;
    private String columnDisplay;
    private String displayType;
    private String possibleValues;
    private String actions;
    private boolean readonly;
    private int orderNumber;
    private boolean deleted;
    private long lastChanged;
    private String changedBy;
    private int maxLength;

    private FieldSettingsDto(Builder builder) {
        this.fieldSettingsId = builder.fieldSettingsId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.fieldType = builder.fieldType;
        this.columnName = builder.columnName;
        this.columnDisplay = builder.columnDisplay;
        this.displayType = builder.displayType;
        this.possibleValues = builder.possibleValues;
        this.actions = builder.actions;
        this.readonly = builder.readonly;
        this.orderNumber = builder.orderNumber;
        this.deleted = builder.deleted;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
        this.maxLength = builder.maxLength;
    }

    public static class Builder {

        private int fieldSettingsId;
        private int ddpInstanceId;
        private String fieldType;
        private String columnName;
        private String columnDisplay;
        private String displayType;
        private String possibleValues;
        private String actions;
        private boolean readonly;
        private int orderNumber;
        private boolean deleted;
        private long lastChanged;
        private String changedBy;
        private int maxLength;

        public Builder(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
        }

        public Builder withFieldSettingsId(int fieldSettingsId) {
            this.fieldSettingsId = fieldSettingsId;
            return this;
        }

        public Builder withFieldType(String fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        public Builder withColumnName(String columnName) {
            this.columnName = columnName;
            return this;
        }

        public Builder withColumnDisplay(String columnDisplay) {
            this.columnDisplay = columnDisplay;
            return this;
        }

        public Builder withDisplayType(String displayType) {
            this.displayType = displayType;
            return this;
        }

        public Builder withPossibleValues(String possibleValues) {
            this.possibleValues = possibleValues;
            return this;
        }

        public Builder withActions(String actions) {
            this.actions = actions;
            return this;
        }

        public Builder withReadOnly(boolean readonly) {
            this.readonly = readonly;
            return this;
        }

        public Builder withOrderNumber(int orderNumber) {
            this.orderNumber = orderNumber;
            return this;
        }

        public Builder withDeleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = changedBy;
            return this;
        }

        public Builder withMaxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }


        public FieldSettingsDto build() {
            return new FieldSettingsDto(this);
        }


    }
}
