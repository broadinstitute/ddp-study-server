package org.broadinstitute.ddp.model.copy;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyAnswerLocation extends CopyLocation {

    private long questionStableCodeId;
    private String questionStableId;

    @JdbiConstructor
    public CopyAnswerLocation(
            @ColumnName("copy_location_id") long id,
            @ColumnName("question_stable_code_id") long questionStableCodeId,
            @ColumnName("question_stable_id") String questionStableId) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
    }

    public CopyAnswerLocation(String questionStableId) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
    }

    public long getQuestionStableCodeId() {
        return questionStableCodeId;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }
}
