package org.broadinstitute.ddp.model.activity.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;

public final class GroupBlockDef extends FormBlockDef {

    @SerializedName("listStyleHint")
    private ListStyleHint listStyleHint;

    @NotNull
    @SerializedName("presentationHint")
    private PresentationHint presentationHint = PresentationHint.DEFAULT;

    @Valid
    @SerializedName("title")
    private Template titleTemplate;

    @NotEmpty
    @SerializedName("nested")
    private List<@Valid @NotNull FormBlockDef> nested = new ArrayList<>();

    public GroupBlockDef() {
        super(BlockType.GROUP);
    }

    public GroupBlockDef(ListStyleHint listStyleHint, Template titleTemplate) {
        super(BlockType.GROUP);
        this.listStyleHint = listStyleHint;
        this.titleTemplate = titleTemplate;
    }

    public ListStyleHint getListStyleHint() {
        return listStyleHint;
    }

    public PresentationHint getPresentationHint() {
        return presentationHint;
    }

    public Template getTitleTemplate() {
        return titleTemplate;
    }

    public List<FormBlockDef> getNested() {
        return nested;
    }

    public void addNestedBlock(FormBlockDef nestedBlock) {
        this.nested.add(nestedBlock);
    }

    @Override
    public Stream<QuestionDef> getQuestions() {
        return getNested().stream().flatMap(cblock -> cblock.getQuestions());
    }
}
