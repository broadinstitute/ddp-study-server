package org.broadinstitute.ddp.model.files;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FileUpload {

    private final long id;
    private final String guid;
    private final long studyId;
    private final long operatorUserId;
    private final long participantUserId;
    private final String blobName;
    private final String mimeType;
    private final String fileName;
    private final long fileSize;
    private final boolean isVerified;
    private final Instant createdAt;
    private final Instant uploadedAt;
    private final Instant scannedAt;
    private final FileScanResult scanResult;

    @JdbiConstructor
    public FileUpload(@ColumnName("file_upload_id") long id,
                      @ColumnName("file_upload_guid") String guid,
                      @ColumnName("study_id") long studyId,
                      @ColumnName("operator_user_id") long operatorUserId,
                      @ColumnName("participant_user_id") long participantUserId,
                      @ColumnName("blob_name") String blobName,
                      @ColumnName("mime_type") String mimeType,
                      @ColumnName("file_name") String fileName,
                      @ColumnName("file_size") long fileSize,
                      @ColumnName("is_verified") boolean isVerified,
                      @ColumnName("created_at") Instant createdAt,
                      @ColumnName("uploaded_at") Instant uploadedAt,
                      @ColumnName("scanned_at") Instant scannedAt,
                      @ColumnName("scan_result") FileScanResult scanResult) {
        this.id = id;
        this.guid = guid;
        this.studyId = studyId;
        this.operatorUserId = operatorUserId;
        this.participantUserId = participantUserId;
        this.blobName = blobName;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.isVerified = isVerified;
        this.createdAt = createdAt;
        this.uploadedAt = uploadedAt;
        this.scannedAt = scannedAt;
        this.scanResult = scanResult;
    }

    /**
     * Returns internal unique identifier.
     */
    public long getId() {
        return id;
    }

    /**
     * Returns external unique identifier.
     */
    public String getGuid() {
        return guid;
    }

    /**
     * Returns identifier of the study.
     */
    public long getStudyId() {
        return studyId;
    }

    /**
     * Returns identifier of operator that initiated this file upload.
     */
    public long getOperatorUserId() {
        return operatorUserId;
    }

    /**
     * Returns identifier of participant that this file upload is for, e.g. the user that owns this file upload.
     */
    public long getParticipantUserId() {
        return participantUserId;
    }

    /**
     * Returns bucket file name. This is the entire file path for the blob in the bucket.
     */
    public String getBlobName() {
        return blobName;
    }

    /**
     * Returns MIME type, such as `application/pdf`.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Returns user-provided name for the file being uploaded.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns user-provided size of the file being uploaded, in bytes.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Returns whether file upload is verified, i.e. if file upload meets criteria such as being uploaded, matches
     * reported file size, etc. Note that currently verification is only performed when user attempts to associate a
     * file upload with an answer.
     */
    public boolean isVerified() {
        return isVerified;
    }

    /**
     * Returns timestamp of when file upload was first created and authorized.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns timestamp of when file was finished uploading, null if not yet uploaded.
     */
    public Instant getUploadedAt() {
        return uploadedAt;
    }

    /**
     * Returns timestamp of when file was scanned, null if not yet finished scanning.
     */
    public Instant getScannedAt() {
        return scannedAt;
    }

    /**
     * Returns scan result of the file upload, null if not yet finished scanning.
     */
    public FileScanResult getScanResult() {
        return scanResult;
    }
}
