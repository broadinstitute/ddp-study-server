package org.broadinstitute.ddp.model.activity.definition;

import java.lang.reflect.Type;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;

@Data
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class FormBlockDef {
    @NotNull
    @SerializedName("blockType")
    private final BlockType blockType;

    @SerializedName("blockGuid")
    private String blockGuid;

    @SerializedName("shownExpr")
    private String shownExpr;

    @SerializedName("enabledExpr")
    private String enabledExpr;

    @SerializedName("columnSpan")
    private int columnSpan;

    private transient Long blockId;
    private transient Long shownExprId;
    private transient Long enabledExprId;

    public abstract Stream<QuestionDef> getQuestions();

    public static class Deserializer implements JsonDeserializer<FormBlockDef> {
        @Override
        public FormBlockDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            final BlockType blockType = parseBlockType(elem);
            switch (blockType) {
                case ACTIVITY:
                    return ctx.deserialize(elem, NestedActivityBlockDef.class);
                case CONTENT:
                    return ctx.deserialize(elem, ContentBlockDef.class);
                case QUESTION:
                    return ctx.deserialize(elem, QuestionBlockDef.class);
                case COMPONENT:
                    return ctx.deserialize(elem, ComponentBlockDef.class);
                case CONDITIONAL:
                    return ctx.deserialize(elem, ConditionalBlockDef.class);
                case TABULAR:
                    return ctx.deserialize(elem, TabularBlockDef.class);
                case GROUP:
                    return ctx.deserialize(elem, GroupBlockDef.class);
                default:
                    throw new JsonParseException(String.format("Block type '%s' is not supported", blockType));
            }
        }

        private BlockType parseBlockType(final JsonElement elem) {
            try {
                return BlockType.valueOf(elem.getAsJsonObject().get("blockType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine block type", e);
            }
        }
    }
}
