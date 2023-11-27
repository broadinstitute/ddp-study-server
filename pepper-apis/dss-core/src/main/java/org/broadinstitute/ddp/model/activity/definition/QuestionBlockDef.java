package org.broadinstitute.ddp.model.activity.definition;

import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

@Getter
public final class QuestionBlockDef extends FormBlockDef {

    @Valid
    @NotNull
    @SerializedName("question")
    private QuestionDef question;

    public QuestionBlockDef(QuestionDef question) {
        super(BlockType.QUESTION);
        this.question = MiscUtil.checkNonNull(question, "question");
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return Stream.of(question);
    }

}
