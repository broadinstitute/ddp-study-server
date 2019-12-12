package org.broadinstitute.ddp.model.activity.definition;

import java.lang.reflect.Type;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

/**
 * Superclass for embedded components such as mailing address,
 * physicians, and institutions
 */
public abstract class ComponentBlockDef extends FormBlockDef {

    @NotNull
    @SerializedName("componentType")
    protected ComponentType componentType;

    @SerializedName("hideNumber")
    private boolean hideNumber;

    public ComponentBlockDef(ComponentType componentType) {
        super(BlockType.COMPONENT);
        this.componentType = componentType;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public boolean shouldHideNumber() {
        return hideNumber;
    }

    public void setHideNumber(boolean hideNumber) {
        this.hideNumber = hideNumber;
    }

    public static class Deserializer implements JsonDeserializer<ComponentBlockDef> {
        @Override
        public ComponentBlockDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            ComponentType componentType = parseComponentType(elem);
            switch (componentType) {
                case MAILING_ADDRESS:
                    return ctx.deserialize(elem, MailingAddressComponentDef.class);
                case PHYSICIAN:
                    return ctx.deserialize(elem, PhysicianComponentDef.class);
                case INSTITUTION:
                    return ctx.deserialize(elem, InstitutionComponentDef.class);
                default:
                    throw new JsonParseException(String.format("Component type '%s' is not supported", componentType));
            }
        }

        private ComponentType parseComponentType(JsonElement elem) {
            try {
                return ComponentType.valueOf(elem.getAsJsonObject().get("componentType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine component type", e);
            }
        }
    }
}
