package org.broadinstitute.ddp.json.study;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.hibernate.validator.constraints.Length;

@Value
@AllArgsConstructor
public class StudyExitRequestPayload {
    @Length(max = 1000)
    @SerializedName("notes")
    String notes;
}
