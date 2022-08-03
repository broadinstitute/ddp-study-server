package org.broadinstitute.ddp.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PubSubAttributes {
    USER_GUID("userGuid"),
    FILE_GUID("fileGuid"),
    TASK_TYPE("taskType");

    private final String value;
}
