package org.broadinstitute.ddp.json.study;

import com.google.gson.annotations.SerializedName;
import org.hibernate.validator.constraints.Length;

public class StudyExitRequestPayload {

    @Length(max = 1000)
    @SerializedName("notes")
    private String notes;

    public StudyExitRequestPayload(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }
}
