package org.broadinstitute.ddp.model.fileupload;

import java.util.Date;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FileUpload {
    private long id;
    private String fileUploadGuid;
    private String bucketFileUrl;
    private Long urlCreationTime;
    private String fileName;
    private Long fileSize;
    private FileUploadStatus status;
    private Date fileCreationTime;

    @JdbiConstructor
    public FileUpload(
            @ColumnName("file_upload_id") long id,
            @ColumnName("file_upload_guid") String fileUploadGuid,
            @ColumnName("bucket_file_url") String bucketFileUrl,
            @ColumnName("url_creation_time") Long urlCreationTime,
            @ColumnName("file_name") String fileName,
            @ColumnName("file_size") Long fileSize,
            @ColumnName("status") FileUploadStatus status,
            @ColumnName("file_creation_time") Date fileCreationTime) {
        this.id = id;
        this.fileUploadGuid = fileUploadGuid;
        this.bucketFileUrl = bucketFileUrl;
        this.urlCreationTime = urlCreationTime;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
        this.fileCreationTime = fileCreationTime;
    }

    public long getId() {
        return id;
    }

    public String getFileUploadGuid() {
        return fileUploadGuid;
    }

    public String getBucketFileUrl() {
        return bucketFileUrl;
    }

    public Long getUrlCreationTime() {
        return urlCreationTime;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public FileUploadStatus getStatus() {
        return status;
    }

    public Date getFileCreationTime() {
        return fileCreationTime;
    }
}
