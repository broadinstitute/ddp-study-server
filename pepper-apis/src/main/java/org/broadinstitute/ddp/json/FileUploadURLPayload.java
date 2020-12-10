package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

public class FileUploadURLPayload {
    @NotBlank
    @SerializedName("fileName")
    String fileName;

    @NotBlank
    @SerializedName("mimeType")
    String mimeType;

    @NotNull
    @SerializedName("fileSize")
    Long fileSize;

    @SerializedName("activityCode")
    String activityCode;

    @SerializedName("activityInstanceGuid")
    String activityInstanceGuid;

    @SerializedName("answerGuid")
    String answerGuid;

    public FileUploadURLPayload(String fileName, Long fileSize, String activityCode, String activityInstanceGuid, String answerGuid,
                                String mimeType) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.activityCode = activityCode;
        this.activityInstanceGuid = activityInstanceGuid;
        this.answerGuid = answerGuid;
        this.mimeType = mimeType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getActivityCode() {
        return activityCode;
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
}
