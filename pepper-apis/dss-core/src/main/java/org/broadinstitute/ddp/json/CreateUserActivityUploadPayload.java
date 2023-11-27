package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class CreateUserActivityUploadPayload {
    @NotBlank
    @SerializedName("questionStableId")
    String questionStableId;

    @NotBlank
    @Size(max = 255)
    @SerializedName("fileName")
    String fileName;

    @Positive
    @SerializedName("fileSize")
    long fileSize;

    @Size(max = 255)
    @SerializedName("mimeType")
    String mimeType;

    @SerializedName("resumable")
    boolean resumable;
}
