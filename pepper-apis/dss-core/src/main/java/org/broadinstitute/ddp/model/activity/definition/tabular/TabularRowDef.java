package org.broadinstitute.ddp.model.activity.definition.tabular;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Value
public class TabularRowDef {
    @NotEmpty
    @SerializedName("questions")
    List<@Valid QuestionDef> questions;
}
