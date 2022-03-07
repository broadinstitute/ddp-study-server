package org.broadinstitute.ddp.model.activity.types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum QuestionType {
    AGREEMENT(false),
    BOOLEAN(false),
    COMPOSITE(false),
    DATE(true),
    FILE(false),
    NUMERIC(true),
    DECIMAL(true),
    PICKLIST(false),
    TEXT(false),
    ACTIVITY_INSTANCE_SELECT(false),
    MATRIX(false);

    private final boolean comparable;
}
