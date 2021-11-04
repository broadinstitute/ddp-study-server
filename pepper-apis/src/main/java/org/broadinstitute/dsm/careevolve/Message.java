package org.broadinstitute.dsm.careevolve;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.Base64;

public class Message {

    @SerializedName("Body")
    private String body;

    @SerializedName("Name")
    private String name;

    public Message(Order order, String messageName) {
        String json = new GsonBuilder().serializeNulls().create().toJson(order);
        // as per CareEvolve's docs, the body field should be base64 encoded json
        this.body = new String(Base64.getEncoder().encode(json.getBytes()));
        this.name = messageName;
    }

    public String getName() {
        return name;
    }
}
