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

    @JdbiConstructor
    public AnswerDto(@ColumnName("answer_id") Long id,
                     @ColumnName("answer_guid") String guid,
                     @ColumnName("question_id") long questionId,
                     @ColumnName("question_stable_id") String questionStableId,
                     @ColumnName("question_type") QuestionType questionType) {
        this.id = id;
        this.guid = guid;
        this.questionId = questionId;
        this.questionStableId = questionStableId;
        this.questionType = questionType;
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
}
