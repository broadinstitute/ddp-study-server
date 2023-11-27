package org.broadinstitute.ddp.model.files;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import javax.annotation.Nullable;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class FileUpload {
    @ColumnName("file_upload_id")
    long id;

    @ColumnName("file_upload_guid")
    String guid;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("operator_user_id")
    long operatorUserId;

    @ColumnName("participant_user_id")
    long participantUserId;

    @ColumnName("blob_name")
    String blobName;

    @ColumnName("mime_type")
    String mimeType;

    @ColumnName("file_name")
    String fileName;

    @ColumnName("file_size")
    long fileSize;

    @ColumnName("is_verified")
    @Accessors(fluent = true)
    boolean isVerified;

    @ColumnName("created_at")
    Instant createdAt;

    @Nullable
    @ColumnName("uploaded_at")
    Instant uploadedAt;

    @Nullable
    @ColumnName("scanned_at")
    Instant scannedAt;

    @Nullable
    @ColumnName("scan_result")
    FileScanResult scanResult;

    @Nullable
    @ColumnName("notification_sent_at")
    Instant notificationSentAt;

    public String getHumanReadableFileSize() {
        return FileUtils.byteCountToDisplaySize(fileSize);
    }
}
