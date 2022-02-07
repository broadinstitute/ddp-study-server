package org.broadinstitute.ddp.model.activity.instance;

import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.util.MiscUtil;

public abstract class FormBlock implements Renderable {

    @NotNull
    @SerializedName("blockType")
    protected BlockType blockType;

    @SerializedName("blockGuid")
    protected String guid;

    @SerializedName("shown")
    protected boolean shown = true;

    @SerializedName("enabled")
    protected boolean enabled = true;

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

    public BlockType getBlockType() {
        return blockType;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public boolean isShown() {
        return shown;
    }

    public void setShown(boolean shown) {
        this.shown = shown;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public String getShownExpr() {
        return shownExpr;
    }

    public void setShownExpr(String shownExpr) {
        this.shownExpr = shownExpr;
    }

    public String getEnabledExpr() {
        return enabledExpr;
    }

    public void setEnabledExpr(String enabledExpr) {
        this.enabledExpr = enabledExpr;
    }
}
