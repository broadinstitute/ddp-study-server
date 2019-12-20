package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ChildAnswerDto extends AnswerDto {

    private final Integer orderIndex;

    @JdbiConstructor
    public ChildAnswerDto(@ColumnName("answer_id") Long id,
                     @ColumnName("answer_guid") String guid,
                     @ColumnName("question_id") long questionId,
                     @ColumnName("question_stable_id") String questionStableId,
                     @ColumnName("question_type") QuestionType questionType,
                     @ColumnName("created_at") long createdAt,
                     @ColumnName("last_updated_at") long lastUpdatedAt, @ColumnName("row_order") Integer orderIndex) {
        super(id, guid, questionId, questionStableId, questionType, createdAt, lastUpdatedAt);
        this.orderIndex = orderIndex;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }
}
