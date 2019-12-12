package org.broadinstitute.ddp.json.export;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

public class FireCloudEntity {

    @SerializedName("entityName")
    private String entityName;

    @SerializedName("name")
    // used only during deletes because firecloud's APIs aren't entirely symmetric
    private String nameFromGetParticipantsEndpoint;

    @SerializedName("entityType")
    private String entityType;

    private Map<String, String> attributes = new HashMap<>();

    /**
     * Construct a FireCloudEntity object based on jsn from FC.
     */
    public FireCloudEntity(String json) {
        JsonObject jsonObject = new Gson().fromJson(json, new TypeToken<JsonObject>(){}.getType());

        this.entityName = jsonObject.get("name").getAsString();

        this.entityType = jsonObject.get("entityType").getAsString();

        this.attributes = new HashMap<>();
        JsonObject attr = jsonObject.get("attributes").getAsJsonObject();

        for (Map.Entry<String, JsonElement> field : attr.entrySet()) {
            attributes.put(field.getKey(), field.getValue().getAsString());
        }
    }

    public FireCloudEntity(String name, String entityType) {
        this.entityName = name;
        this.entityType = entityType;
    }

    public FireCloudEntity(String name, Map<String, String> attributes, String entityType) {
        this(name, entityType);
        this.attributes = attributes;
        this.entityType = entityType;
    }

    /**
     * Firecloud's get participant endpoint considers the participant's name
     * field to be called "name", whereas other endpoints (such as delete) are looking
     * for "entityName".  This method takes the name returned from the get participant
     * endpoint and applies it to entityName.
     */
    public void setEntityNameFromNameFromGetParticipantsEndpoint() {
        this.entityName = nameFromGetParticipantsEndpoint;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    public String getEntityName() {
        return entityName;
    }

    public String toTSVRow(Collection<String> header) {
        StringBuilder row = new StringBuilder();
        row.append(entityName);
        for (String attribute : header) {
            if (attributes.containsKey(attribute)) {
                row.append("\t" + attributes.get(attribute));
            } else {
                row.append("\t");
            }
        }
        return row.toString();
    }
}
