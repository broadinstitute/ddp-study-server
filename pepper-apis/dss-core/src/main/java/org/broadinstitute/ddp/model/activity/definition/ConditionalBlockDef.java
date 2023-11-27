package org.broadinstitute.ddp.model.activity.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

public final class ConditionalBlockDef extends FormBlockDef {

    @Valid
    @NotNull
    @SerializedName("control")
    private QuestionDef control;

    @NotEmpty
    @SerializedName("nested")
    private List<@Valid @NotNull FormBlockDef> nested = new ArrayList<>();

    public ConditionalBlockDef(QuestionDef control) {
        super(BlockType.CONDITIONAL);
        this.control = control;
    }

    public QuestionDef getControl() {
        return control;
    }

    public List<FormBlockDef> getNested() {
        return nested;
    }

    public void addNestedBlock(FormBlockDef block) {
        nested.add(block);
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        Stream<QuestionDef> nestedQuestions = getNested().stream().flatMap(cblock -> cblock.getQuestions());
        return Stream.concat(Stream.of(control), nestedQuestions);
    }
}
