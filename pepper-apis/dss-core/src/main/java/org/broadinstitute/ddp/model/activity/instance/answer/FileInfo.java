package org.broadinstitute.ddp.model.activity.instance.answer;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

import com.google.gson.annotations.SerializedName;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FileInfo implements Serializable {

    @NotBlank
    @SerializedName("fileName")
    private String fileName;

    @Positive
    @SerializedName("fileSize")
    private long fileSize;

    private transient long uploadId;

    @SerializedName("uploadGuid")
    private String uploadGuid;

    @JdbiConstructor
    public FileInfo(@ColumnName("file_upload_id") long uploadId,
                    @ColumnName("file_upload_guid") String uploadGuid,
                    @ColumnName("file_name") String fileName,
                    @ColumnName("file_size") long fileSize) {
        this.uploadId = uploadId;
        this.uploadGuid = uploadGuid;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public long getUploadId() {
        return uploadId;
    }

    public String getUploadGuid() {
        return uploadGuid;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }
}
