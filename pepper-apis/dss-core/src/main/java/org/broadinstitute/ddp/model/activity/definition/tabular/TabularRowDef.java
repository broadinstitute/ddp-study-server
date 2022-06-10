package org.broadinstitute.ddp.model.activity.definition.tabular;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Value
public class TabularRowDef {
    //@NotEmpty
    //@SerializedName("columns")
    //List<TabularColumnDef> tabularColumnDefs;

    @NotEmpty
    @SerializedName("columns")
    private List<@Valid @NotNull QuestionBlockDef> tabularColumnDefs = new ArrayList<>();

}
