package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.addNonNullsToSet;

import java.io.Serializable;
import java.util.Set;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

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
