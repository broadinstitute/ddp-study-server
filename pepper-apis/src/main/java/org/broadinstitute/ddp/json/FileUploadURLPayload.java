package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;

public class FileUploadURLPayload {
    @NotBlank
    @Size(max = 200, message = "fileName shouldn't be longer than 200 characters")
    @SerializedName("fileName")
    String fileName;

    @NotBlank
    @SerializedName("mimeType")
    String mimeType;

    @NotNull
    @Positive
    @SerializedName("fileSize")
    Long fileSize;

    @NotNull
    @SerializedName("resumable")
    Boolean resumable;

    @SerializedName("activityInstanceGuid")
    String activityInstanceGuid;

    @SerializedName("answerGuid")
    String answerGuid;

    public FileUploadURLPayload(String fileName, Long fileSize, String activityInstanceGuid, String answerGuid,
                                String mimeType, Boolean resumable) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.activityInstanceGuid = activityInstanceGuid;
        this.answerGuid = answerGuid;
        this.mimeType = mimeType;
        this.resumable = resumable;
    }

    public String getFileName() {
        return fileName;
    }

    public String getActivityInstanceGuid() {
        return activityInstanceGuid;
    }

    public String getAnswerGuid() {
        return answerGuid;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Boolean getResumable() {
        return resumable;
    }
}
