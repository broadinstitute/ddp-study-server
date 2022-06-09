package org.broadinstitute.ddp.model.activity.instance;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.tabular.TabularHeader;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Getter
public final class TabularBlock extends FormBlock {
    @SerializedName("headers")
    private final List<TabularHeader> headers;

    @SerializedName("content")
    private final List<Question> rows;

    private final int columnsCount;

    public TabularBlock(int columnsCount, List<TabularHeader> headers, List<Question> rows) {
        super(BlockType.TABULAR);

        this.columnsCount = columnsCount;
        this.headers = headers;
        this.rows = rows;
    }

    /*public Question get(final int row, final int column) {
        if (row < 0 || row >= rows.size()) {
            throw new IndexOutOfBoundsException("The row must be between 0 and " + rows.size());
        }

        final List<Question> cellsRow = rows.get(row);
        if (column < 0 || column >= cellsRow.size()) {
            throw new IndexOutOfBoundsException("The column must be between 0 and " + cellsRow.size());
        }

        return cellsRow.get(column);
    }*/

    @Override
    public Stream<Question> streamQuestions() {
        //return StreamEx.of(rows).flatMap(Collection::stream).filter(Objects::nonNull);
        return StreamEx.of(rows).filter(Objects::nonNull);
    }

    @Override
    public boolean isComplete() {
        return !shown || streamQuestions().allMatch(Question::passesDeferredValidations);
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        StreamEx.of(headers).map(TabularHeader::getLabelTemplateId).forEach(registry);
        streamQuestions().forEach(question -> question.registerTemplateIds(registry));
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        StreamEx.of(headers).forEach(header -> header.setLabel(rendered.get(header.getLabelTemplateId())));
        streamQuestions().forEach(question -> question.applyRenderedTemplates(rendered, style));
    }
}
