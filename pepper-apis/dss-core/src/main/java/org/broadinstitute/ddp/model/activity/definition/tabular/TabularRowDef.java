package org.broadinstitute.ddp.model.activity.definition.tabular;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Value
public class TabularRowDef {
    @NotEmpty
    @SerializedName("tabularColumnDefs")
    List<TabularColumnDef> tabularColumnDefs;
}
