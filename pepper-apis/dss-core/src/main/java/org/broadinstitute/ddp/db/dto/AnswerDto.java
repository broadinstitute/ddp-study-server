package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class AnswerDto {
    @ColumnName("answer_id")
    Long id;
    
    @ColumnName("answer_guid")
    String guid;

    @ColumnName("last_updated_at")
    long lastUpdatedAt;

    @ColumnName("question_id")
    long questionId;

    @ColumnName("question_stable_id")
    String questionStableId;

    @ColumnName("question_type")
    QuestionType questionType;

    @ColumnName("activity_instance_id")
    long activityInstanceId;
}
