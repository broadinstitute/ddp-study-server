package org.broadinstitute.ddp.model.activity.instance;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.tabular.TabularContent;
import org.broadinstitute.ddp.model.activity.instance.tabular.TabularHeader;
import org.broadinstitute.ddp.model.activity.types.BlockType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Getter
public final class TabularBlock extends FormBlock {
    @SerializedName("headers")
    private final List<TabularHeader> headers = new ArrayList<>();

    @SerializedName("rows")
    private final List<TabularContent> rows = new ArrayList<>();

    private int columnsCount;

    public TabularBlock() {
        super(BlockType.TABULAR);
    }

    @Override
    public Stream<Question> streamQuestions() {
        return StreamEx.of(rows).map(TabularContent::getQuestions).flatMap(Collection::stream);
    }

    @Override
    public boolean isComplete() {
        return !shown || streamQuestions().allMatch(Question::passesDeferredValidations);
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        streamQuestions().forEach(question -> registerTemplateIds(registry));
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        streamQuestions().forEach(question -> question.applyRenderedTemplates(rendered, style));
    }
}
