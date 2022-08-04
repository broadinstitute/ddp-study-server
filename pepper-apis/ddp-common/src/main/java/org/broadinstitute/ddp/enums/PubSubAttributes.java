package org.broadinstitute.ddp.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PubSubAttributes {
    STUDY_GUID("studyGuid"),
    USER_GUID("userGuid"),
    FILE_GUID("fileGuid"),
    TASK_TYPE("taskType"),
    USER_NAME("userName"),
    FILE_NAME("fileName");

    private final String value;
}
