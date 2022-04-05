package org.broadinstitute.ddp.json.export;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Workspace {
    @SerializedName("name")
    String name;

    @SerializedName("namespace")
    String namespace;
}
