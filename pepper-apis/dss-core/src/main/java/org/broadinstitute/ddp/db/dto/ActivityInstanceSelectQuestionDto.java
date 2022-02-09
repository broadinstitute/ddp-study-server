package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.addNonNullsToSet;

public final class ActivityInstanceSelectQuestionDto extends QuestionDto implements Serializable {

    @JdbiConstructor
    public ActivityInstanceSelectQuestionDto(@Nested QuestionDto questionDto) {
        super(questionDto);
    }

    @Override
    public Set<Long> getTemplateIds() {
        return addNonNullsToSet(super.getTemplateIds());
    }
}
