package org.broadinstitute.ddp.model.activity.instance;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.Renderable;
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

    protected transient Long blockId;
    protected transient String shownExpr;

    FormBlock(BlockType type) {
        this.blockType = MiscUtil.checkNonNull(type, "type");
    }

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

}
