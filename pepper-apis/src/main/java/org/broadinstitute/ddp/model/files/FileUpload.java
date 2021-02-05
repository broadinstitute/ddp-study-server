package org.broadinstitute.ddp.model.files;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FileUpload {

    private final long id;
    private final String guid;
    private final String blobName;
    private final String mimeType;
    private final String fileName;
    private final long fileSize;
    private final long operatorUserId;
    private final long participantUserId;
    private final FileUploadStatus status;
    private final Instant statusChangedAt;
    private final Instant createdAt;
    private final boolean isReplaced;

    @JdbiConstructor
    public FileUpload(@ColumnName("file_upload_id") long id,
                      @ColumnName("file_upload_guid") String guid,
                      @ColumnName("blob_name") String blobName,
                      @ColumnName("mime_type") String mimeType,
                      @ColumnName("file_name") String fileName,
                      @ColumnName("file_size") long fileSize,
                      @ColumnName("operator_user_id") long operatorUserId,
                      @ColumnName("participant_user_id") long participantUserId,
                      @ColumnName("status") FileUploadStatus status,
                      @ColumnName("status_changed_at") Instant statusChangedAt,
                      @ColumnName("created_at") Instant createdAt,
                      @ColumnName("is_replaced") boolean isReplaced) {
        this.id = id;
        this.guid = guid;
        this.blobName = blobName;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.operatorUserId = operatorUserId;
        this.participantUserId = participantUserId;
        this.status = status;
        this.statusChangedAt = statusChangedAt;
        this.createdAt = createdAt;
        this.isReplaced = isReplaced;
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
     * Returns current status of the file upload.
     */
    public FileUploadStatus getStatus() {
        return status;
    }

    /**
     * Returns timestamp of the latest status change.
     */
    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    /**
     * Returns timestamp of when file upload was first created and authorized.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Whether this file upload has been replaced by another upload. Replaced uploads should be removed.
     */
    public boolean isReplaced() {
        return isReplaced;
    }
}
