package org.broadinstitute.ddp.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum DSMTaskType {
    CLEAR_BEFORE_UPDATE("clearBeforeUpdate"),
    ELASTIC_EXPORT("ELASTIC_EXPORT"),
    FILE_UPLOADED("FILE_UPLOADED"),
    PARTICIPANT_REGISTERED("PARTICIPANT_REGISTERED"),
    UPDATE_CUSTOM_WORKFLOW("UPDATE_CUSTOM_WORKFLOW");

    private final String value;

    public static DSMTaskType of(final String value) {
        for (final DSMTaskType taskType : DSMTaskType.values()) {
            if (taskType.value.equals(value)) {
                return taskType;
            }
        }

        return null;
    }
}
