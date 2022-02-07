package org.broadinstitute.ddp.model.activity.definition;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionStateTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable;

import java.beans.ConstructorProperties;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.broadinstitute.ddp.util.MiscUtil;

public class SectionIcon {

    public static final String REQUIRED_SCALE_FACTOR = "1x";

    private static final String PROP_STATE = "state";
    private static final String PROP_HEIGHT = "height";
    private static final String PROP_WIDTH = "width";

    @NotNull
    @SerializedName(PROP_STATE)
    private FormSectionState state;

    @Min(0)
    @SerializedName(PROP_HEIGHT)
    private int height;

    @Min(0)
    @SerializedName(PROP_WIDTH)
    private int width;

    @NotEmpty
    @SerializedName("sources")
    private Map<String, URL> sources = new HashMap<>();

    private transient Long iconId;
    private transient Long sectionId;

    public SectionIcon(FormSectionState state, int height, int width) {
        this.state = MiscUtil.checkNonNull(state, "form section state");
        this.height = MiscUtil.checkPositiveOrZero(height, "height points");
        this.width = MiscUtil.checkPositiveOrZero(width, "width points");
    }

    @ConstructorProperties({FormSectionIconTable.ID, FormSectionTable.ID,
            FormSectionStateTable.CODE, FormSectionIconTable.HEIGHT, FormSectionIconTable.WIDTH})
    public SectionIcon(long iconId, long sectionId, FormSectionState state, int height, int width) {
        this(state, height, width);
        this.iconId = iconId;
        this.sectionId = sectionId;
    }

    public SectionIcon(FormSectionState state, int height, int width, Map<String, URL> sources) {
        this(state, height, width);
        if (sources != null) {
            this.sources.putAll(sources);
        }
    }

    public Long getIconId() {
        return iconId;
    }

    public void setIconId(Long iconId) {
        this.iconId = iconId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }

    public FormSectionState getState() {
        return state;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public Map<String, URL> getSources() {
        return sources;
    }

    public void putSource(String scale, URL url) {
        this.sources.put(scale, url);
    }

    public boolean hasRequiredScaleFactor() {
        return sources.get(REQUIRED_SCALE_FACTOR) != null;
    }

    public static class Serializer implements JsonSerializer<SectionIcon> {
        @Override
        public JsonElement serialize(SectionIcon icon, Type type, JsonSerializationContext context) {
            JsonObject json = new JsonObject();
            json.addProperty(PROP_STATE, icon.state.name());
            json.addProperty(PROP_HEIGHT, icon.height);
            json.addProperty(PROP_WIDTH, icon.width);
            for (Map.Entry<String, URL> entry : icon.sources.entrySet()) {
                json.addProperty(entry.getKey(), entry.getValue().toString());
            }
            return json;
        }
    }

    public static class Deserializer implements JsonDeserializer<SectionIcon> {
        @Override
        public SectionIcon deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            if (!elem.isJsonObject()) {
                throw new JsonParseException("Expected a json object for section icon");
            }

            Map<String, URL> sources = new HashMap<>();
            FormSectionState state = null;
            Integer height = null;
            Integer width = null;

            for (Map.Entry<String, JsonElement> pair : elem.getAsJsonObject().entrySet()) {
                String key = pair.getKey();
                if (PROP_STATE.equals(key)) {
                    state = convertState(pair.getValue());
                } else if (PROP_HEIGHT.equals(key)) {
                    height = convertInt(pair.getValue(), key);
                } else if (PROP_WIDTH.equals(key)) {
                    width = convertInt(pair.getValue(), key);
                } else if (key.endsWith("x")) {
                    sources.put(key, convertUrl(pair.getValue(), key));
                }
                // Otherwise, ignored.
            }

            if (state == null) {
                throw new JsonParseException("Missing state property for section icon");
            }
            if (height == null) {
                throw new JsonParseException("Missing height property for section icon");
            }
            if (width == null) {
                throw new JsonParseException("Missing width property for section icon");
            }
            if (sources.isEmpty() || !sources.containsKey(REQUIRED_SCALE_FACTOR)) {
                throw new JsonParseException("Missing required scale factor " + REQUIRED_SCALE_FACTOR + " for section icon");
            }

            return new SectionIcon(state, height, width, sources);
        }

        private FormSectionState convertState(JsonElement elem) {
            try {
                return FormSectionState.valueOf(elem.getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine section icon state", e);
            }
        }

        private int convertInt(JsonElement elem, String key) {
            try {
                return elem.getAsInt();
            } catch (Exception e) {
                throw new JsonParseException("Could not parse section icon " + key, e);
            }
        }

        private URL convertUrl(JsonElement elem, String scale) {
            try {
                return new URL(elem.getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not parse section icon url for scale factor " + scale, e);
            }
        }
    }
}
