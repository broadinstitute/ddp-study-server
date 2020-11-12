package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class AgreementQuestionDto extends QuestionDto implements Serializable {

    @JdbiConstructor
    public AgreementQuestionDto(@Nested QuestionDto questionDto) {
        super(questionDto);
    }
}
