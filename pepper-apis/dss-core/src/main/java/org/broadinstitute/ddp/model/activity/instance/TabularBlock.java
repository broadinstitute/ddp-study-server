package org.broadinstitute.ddp.model.activity.instance;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.tabular.TabularHeader;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public final class TabularBlock extends FormBlock {
    @SerializedName("headers")
    private final List<TabularHeader> headers;

    @SerializedName("content")
    private final List<QuestionBlockDef> rows;

    //private final List<Question> questions;

    private final int columnsCount;

    public TabularBlock(int columnsCount, List<TabularHeader> headers, List<QuestionBlockDef> rows) {
        super(BlockType.TABULAR);

        this.columnsCount = columnsCount;
        this.headers = headers;
        this.rows = rows;
    }

    @Override
    public Stream<Question> streamQuestions() {
        //return rows.stream().flatMap(QuestionBlockDef::getQuestions).collect(Collectors.toList()).stream();
        return StreamEx.of(rows).nonNull().collect(Collectors.toMap(QuestionBlockDef::getQuestion));
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
