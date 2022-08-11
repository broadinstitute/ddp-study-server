package org.broadinstitute.dsm.model.elastic;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
public class Files {

    @SerializedName(ESObjectConstants.GUID)
    private String guid;
    @SerializedName(ESObjectConstants.BUCKET)
    private String bucket;
    @SerializedName(ESObjectConstants.BLOB_NAME)
    private String blobName;
    @SerializedName(ESObjectConstants.FILE_NAME)
    private String fileName;
    @SerializedName(ESObjectConstants.FILE_SIZE)
    private Long fileSize;
    @SerializedName(ESObjectConstants.MIME_TYPE)
    private String mimeType;
    @SerializedName(ESObjectConstants.AUTHORIZED_AT)
    private String authorizedAt;
    @SerializedName(ESObjectConstants.UPLOADED_AT)
    private String uploadedAt;
    @SerializedName(ESObjectConstants.SCANNED_AT)
    private String scannedAt;
    @SerializedName(ESObjectConstants.SCAN_RESULT)
    private String scanResult;

    public boolean isFileClean() {
        return this.scannedAt != null && "CLEAN".equals(this.scanResult);
    }
}
