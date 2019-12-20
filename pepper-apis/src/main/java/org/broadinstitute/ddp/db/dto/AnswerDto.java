package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class AnswerDto {

    private Long id;
    private String guid;
    private long questionId;
    private String questionStableId;
    private QuestionType questionType;
    private long createdAt;
    private long lastUpdatedAt;

    @JdbiConstructor
    public AnswerDto(@ColumnName("answer_id") Long id,
                     @ColumnName("answer_guid") String guid,
                     @ColumnName("question_id") long questionId,
                     @ColumnName("question_stable_id") String questionStableId,
                     @ColumnName("question_type") QuestionType questionType,
                     @ColumnName("created_at") long createdAt,
                     @ColumnName("last_updated_at") long lastUpdatedAt) {
        this.id = id;
        this.guid = guid;
        this.questionId = questionId;
        this.questionStableId = questionStableId;
        this.questionType = questionType;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public long getQuestionId() {
        return questionId;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
