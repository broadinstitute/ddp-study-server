package org.broadinstitute.ddp.model.activity.definition;

import java.lang.reflect.Type;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

public abstract class FormBlockDef {

    @NotNull
    @SerializedName("blockType")
    private BlockType blockType;

    @SerializedName("blockGuid")
    private String blockGuid;

    @SerializedName("shownExpr")
    private String shownExpr;

    private transient Long blockId;
    private transient Long shownExprId;

    FormBlockDef(BlockType blockType) {
        this.blockType = blockType;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public String getBlockGuid() {
        return blockGuid;
    }

    public void setBlockGuid(String blockGuid) {
        this.blockGuid = blockGuid;
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

    public Long getShownExprId() {
        return shownExprId;
    }

    public void setShownExprId(Long shownExprId) {
        this.shownExprId = shownExprId;
    }

    public abstract Stream<QuestionDef> getQuestions();

    public static class Deserializer implements JsonDeserializer<FormBlockDef> {
        @Override
        public FormBlockDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            BlockType blockType = parseBlockType(elem);
            switch (blockType) {
                case CONTENT:
                    return ctx.deserialize(elem, ContentBlockDef.class);
                case QUESTION:
                    return ctx.deserialize(elem, QuestionBlockDef.class);
                case COMPONENT:
                    return ctx.deserialize(elem, ComponentBlockDef.class);
                case CONDITIONAL:
                    return ctx.deserialize(elem, ConditionalBlockDef.class);
                case GROUP:
                    return ctx.deserialize(elem, GroupBlockDef.class);
                default:
                    throw new JsonParseException(String.format("Block type '%s' is not supported", blockType));
            }
        }

        private BlockType parseBlockType(JsonElement elem) {
            try {
                return BlockType.valueOf(elem.getAsJsonObject().get("blockType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine block type", e);
            }
        }
    }
}
