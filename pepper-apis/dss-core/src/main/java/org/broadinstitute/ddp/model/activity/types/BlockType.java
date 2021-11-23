package org.broadinstitute.ddp.model.activity.types;

import java.util.Arrays;

public enum BlockType {
    // Unit blocks.
    COMPONENT,
    CONTENT,
    QUESTION,

    // Container blocks.
    ACTIVITY,
    CONDITIONAL,
    GROUP;

    public boolean isContainerBlock() {
        return Arrays.asList(ACTIVITY, CONDITIONAL, GROUP).contains(this);
    }

    public boolean isQuestionBlock() {
        return Arrays.asList(CONDITIONAL, QUESTION).contains(this);
    }
}
