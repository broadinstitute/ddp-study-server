package org.broadinstitute.ddp.model.activity.definition.tabular;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Value
public class TabularRowDef {

    @NotEmpty
    @SerializedName("questions")
    List<@Valid QuestionBlockDef> questions = new ArrayList<>();

}
