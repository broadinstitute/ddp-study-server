package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * DTO class to represent matrix question that includes all base question data.
 */
public final class MatrixQuestionDto extends QuestionDto implements Serializable {

    private MatrixSelectMode selectMode;

    @JdbiConstructor
    public MatrixQuestionDto(@Nested QuestionDto questionDto,
                             @ColumnName("matrix_select_mode") MatrixSelectMode selectMode) {
        super(questionDto);
        this.selectMode = selectMode;
    }

    public MatrixSelectMode getSelectMode() {
        return selectMode;
    }

    @Override
    public Set<Long> getTemplateIds() {
        var ids = super.getTemplateIds();
        return ids;
    }
}
