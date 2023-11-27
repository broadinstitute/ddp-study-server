package org.broadinstitute.ddp.model.activity.instance;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

@Data
public abstract class FormBlock implements Renderable {
    @NotNull
    @SerializedName("blockType")
    protected final BlockType blockType;

    @SerializedName("blockGuid")
    protected String guid;

    @SerializedName("shown")
    protected boolean shown = true;

    @SerializedName("enabled")
    protected boolean enabled = true;

    @Nullable
    @SerializedName("columnSpan")
    protected Integer columnSpan;

    protected transient Long blockId;
    protected transient String shownExpr;
    protected transient String enabledExpr;

    FormBlock(BlockType type) {
        this.blockType = MiscUtil.checkNonNull(type, "type");
    }

    /**
     * Return a stream of all questions contained in this block.
     */
    public abstract Stream<Question> streamQuestions();

    /**
     * Has the user completed what's required for this block?
     *
     * @return true if complete, otherwise false
     */
    public abstract boolean isComplete();
}
