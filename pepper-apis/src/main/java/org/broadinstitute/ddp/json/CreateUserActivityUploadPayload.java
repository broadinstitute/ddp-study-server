package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;

public class CreateUserActivityUploadPayload {

    @NotBlank
    @SerializedName("questionStableId")
    private String questionStableId;

    @NotBlank
    @Size(max = 255)
    @SerializedName("fileName")
    private String fileName;

    @Positive
    @SerializedName("fileSize")
    private long fileSize;

    @Size(max = 255)
    @SerializedName("mimeType")
    private String mimeType;

    @SerializedName("resumable")
    private boolean resumable;

    public CreateUserActivityUploadPayload(String questionStableId, String fileName, long fileSize, String mimeType, boolean resumable) {
        this.questionStableId = questionStableId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.resumable = resumable;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isResumable() {
        return resumable;
    }
}
