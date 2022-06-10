package org.broadinstitute.ddp.model.activity.definition;

import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class QuestionBlockDef extends FormBlockDef {

    @Valid
    @NotNull
    @SerializedName("question")
    private QuestionDef question;

    @SerializedName("columnSpan")
    int columnSpan;

    public QuestionBlockDef(QuestionDef question) {
        super(BlockType.QUESTION);
        this.question = MiscUtil.checkNonNull(question, "question");
    }

    public QuestionBlockDef(QuestionDef question, int columnSpan) {
        super(BlockType.QUESTION);
        this.question = MiscUtil.checkNonNull(question, "question");
        this.columnSpan = columnSpan;
    }

    public QuestionDef getQuestion() {
        return question;
    }

    public int getColumnSpan() {
        return columnSpan;
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return Stream.of(question);
    }

}
