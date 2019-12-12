package org.broadinstitute.ddp.model.activity.definition;

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

    public QuestionBlockDef(QuestionDef question) {
        super(BlockType.QUESTION);
        this.question = MiscUtil.checkNonNull(question, "question");
    }

    public QuestionDef getQuestion() {
        return question;
    }
}
