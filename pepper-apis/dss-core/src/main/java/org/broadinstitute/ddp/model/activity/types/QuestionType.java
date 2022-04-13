package org.broadinstitute.ddp.model.activity.types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum QuestionType {
    AGREEMENT(false, false),
    BOOLEAN(false, false),
    COMPOSITE(false, false),
    DATE(true, true),
    FILE(false, false),
    NUMERIC(true, true),
    DECIMAL(true, true),
    EQUATION(false, true),
    PICKLIST(false, true),
    TEXT(false, true),
    ACTIVITY_INSTANCE_SELECT(false, false),
    MATRIX(false, false);

    private final boolean comparable;
    private final boolean compositional;
}
