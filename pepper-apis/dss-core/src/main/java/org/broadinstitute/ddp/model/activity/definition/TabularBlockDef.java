package org.broadinstitute.ddp.model.activity.definition;

import lombok.NonNull;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Value
public class TabularBlockDef extends FormBlockDef {
    int columnsCount;
    List<@Valid @NonNull TabularHeaderDef> headers = new ArrayList<>();
    List<@Valid FormBlockDef> blocks = new ArrayList<>();

    public TabularBlockDef(final int columnsCount) {
        super(BlockType.TABULAR);
        this.columnsCount = columnsCount;
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return getBlocks().stream().flatMap(cblock -> cblock.getQuestions());
    }

}
