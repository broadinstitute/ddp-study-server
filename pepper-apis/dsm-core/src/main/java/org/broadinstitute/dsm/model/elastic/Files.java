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
    private Long authorizedAt;
    @SerializedName(ESObjectConstants.UPLOADED_AT)
    private Long uploadedAt;
    @SerializedName(ESObjectConstants.SCANNED_AT)
    private Long scannedAt;
    @SerializedName(ESObjectConstants.SCAN_RESULT)
    private String scanResult;

}
