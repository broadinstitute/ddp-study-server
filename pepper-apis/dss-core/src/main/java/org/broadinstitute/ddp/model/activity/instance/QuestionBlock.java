package org.broadinstitute.ddp.model.activity.instance;

import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class QuestionBlock extends FormBlock implements Numberable {

    @NotNull
    @SerializedName("question")
    private Question question;

    @SerializedName(DISPLAY_NUMBER)
    private Integer displayNumber;

    private transient boolean hideDisplayNumber;

    public QuestionBlock(Question question, Integer columnSpan) {
        super(BlockType.QUESTION);
        this.question = MiscUtil.checkNonNull(question, "question");
        this.columnSpan = columnSpan;
        hideDisplayNumber = question.shouldHideQuestionNumber();
    }

    public QuestionBlock(Question question) {
        this(question, null);
    }

    public Question getQuestion() {
        return question;
    }

    @Override
    public Stream<Question> streamQuestions() {
        return Stream.of(question);
    }

    @Override
    public boolean isComplete() {
        return !shown || !enabled || question.passesDeferredValidations();
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        question.registerTemplateIds(registry);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        question.applyRenderedTemplates(rendered, style);
    }

    @Override
    public void setDisplayNumber(Integer displayNumber) {
        this.displayNumber = displayNumber;
    }

    @Override
    public Integer getDisplayNumber() {
        return displayNumber;
    }

    public boolean shouldHideNumber() {
        return hideDisplayNumber;
    }

}
