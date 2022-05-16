package org.broadinstitute.ddp.model.activity.definition;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularRowDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Value
public class TabularBlockDef extends FormBlockDef {
    int columnsCount;

    @NotEmpty
    List<@Valid @NotNull TabularHeaderDef> headers = new ArrayList<>();

    @NotEmpty
    List<@Valid @NotNull TabularRowDef> rows = new ArrayList<>();

    public TabularBlockDef(final int columnsCount) {
        super(BlockType.TABULAR);

        this.columnsCount = columnsCount;
    }
    
    public QuestionDef get(final int row, final int column) {
        if ((row < 0) || (row >= rows.size())) {
            throw new IllegalArgumentException("The row must be a number between 0 and " + rows.size());
        }
        
        final TabularRowDef rowDef = rows.get(row);
        if ((column < 0) || (column >= rowDef.getQuestions().size())) {
            throw new IllegalArgumentException("The column must be a number between 0 and " + rowDef.getQuestions().size());
        }

        return rowDef.getQuestions().get(column);
    }

    public int getRowsCount() {
        return rows.size();
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return getRows().stream().map(TabularRowDef::getQuestions).flatMap(Collection::stream);
    }
}
