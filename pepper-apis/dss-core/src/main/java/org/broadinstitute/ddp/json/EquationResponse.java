package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;

import java.util.List;

@Value
@AllArgsConstructor
public class EquationResponse {
    @SerializedName("stableId")
    String questionStableId;

    @SerializedName("values")
    List<DecimalDef> values;
}
