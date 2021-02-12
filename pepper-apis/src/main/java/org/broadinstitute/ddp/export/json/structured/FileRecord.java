package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;

public class FileRecord {

    @SerializedName("guid")
    private String uploadGuid;
    @SerializedName("bucket")
    private String bucket;
    @SerializedName("blobName")
    private String blobName;
    @SerializedName("fileName")
    private String fileName;
    @SerializedName("fileSize")
    private long fileSize;
    @SerializedName("mimeType")
    private String mimeType;
    @SerializedName("authorizedAt")
    private String authorizedAt;
    @SerializedName("uploadedAt")
    private String uploadedAt;
    @SerializedName("scannedAt")
    private String scannedAt;
    @SerializedName("scanResult")
    private FileScanResult scanResult;

    public FileRecord(String bucket, FileUpload upload) {
        this.bucket = bucket;
        this.uploadGuid = upload.getGuid();
        this.blobName = upload.getBlobName();
        this.fileName = upload.getFileName();
        this.fileSize = upload.getFileSize();
        this.mimeType = upload.getMimeType();
        this.authorizedAt = upload.getCreatedAt().toString();
        this.uploadedAt = upload.getUploadedAt() != null ? upload.getUploadedAt().toString() : null;
        this.scannedAt = upload.getScannedAt() != null ? upload.getScannedAt().toString() : null;
        this.scanResult = upload.getScanResult();
        // For extra security, do not export file location if file doesn't have a clean scan result.
        if (this.scanResult != FileScanResult.CLEAN) {
            this.bucket = null;
            this.blobName = null;
        }
    }
}
