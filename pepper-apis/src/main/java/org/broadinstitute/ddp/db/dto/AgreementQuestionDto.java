package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class AgreementQuestionDto extends QuestionDto {

    @JdbiConstructor
    public AgreementQuestionDto(@Nested QuestionDto questionDto) {
        super(questionDto);
    }
}
