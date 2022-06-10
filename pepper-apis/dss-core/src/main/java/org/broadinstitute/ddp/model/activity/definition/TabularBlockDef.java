package org.broadinstitute.ddp.model.activity.definition;

import lombok.NonNull;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularRowDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
public class TabularBlockDef extends FormBlockDef {
    int columnsCount;
    List<@Valid @NonNull TabularHeaderDef> headers = new ArrayList<>();
    List<@Valid @NonNull TabularRowDef> rows = new ArrayList<>();

    public TabularBlockDef(final int columnsCount) {
        super(BlockType.TABULAR);

        this.columnsCount = columnsCount;
    }
    
    public QuestionBlockDef get(final int row, final int column) {
        if ((row < 0) || (row >= rows.size())) {
            throw new IndexOutOfBoundsException("The row must be a number between 0 and " + rows.size());
        }
        
        final TabularRowDef rowDef = rows.get(row);
        if ((column < 0) || (column >= rowDef.getQuestions().size())) {
            throw new IndexOutOfBoundsException("The column must be a number between 0 and "
                    + rowDef.getQuestions().size());
        }

        return rowDef.getQuestions().get(column);
    }

    public int getRowsCount() {
        return rows.size();
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        List<TabularRowDef> rowDefs = getRows().stream().collect(Collectors.toList());
        List<QuestionDef> questionDefList = new ArrayList<>();
        for (TabularRowDef rowDef : rowDefs) {
            for (QuestionBlockDef colDef: rowDef.getQuestions()) {
                if (colDef != null) {
                    questionDefList.add(colDef.getQuestion());
                }
            }
        }
        return questionDefList.stream();

    }
}
