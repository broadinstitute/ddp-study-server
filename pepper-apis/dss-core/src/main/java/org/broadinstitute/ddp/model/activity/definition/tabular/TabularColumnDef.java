package org.broadinstitute.ddp.model.activity.definition.tabular;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;

import javax.validation.constraints.NotEmpty;

@Value
public class TabularColumnDef {

    @NotEmpty
    @SerializedName("columnSpan")
    int columnSpan;

    @NotEmpty
    @SerializedName("question")
    QuestionDef question;

}
