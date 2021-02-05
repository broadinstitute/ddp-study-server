package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface FileUploadSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into file_upload (file_upload_guid, blob_name, mime_type,"
            + "        file_name, file_size, operator_user_id, participant_user_id,"
            + "        is_verified, created_at, uploaded_at, scanned_at, scan_result_id)"
            + " values (:guid, :blobName, :mimeType, :fileName, :fileSize, :operatorId, :participantId,"
            + "        false, :createdAt, null, null, null)")
    long insert(
            @Bind("guid") String fileUploadGuid,
            @Bind("blobName") String blobName,
            @Bind("mimeType") String mimeType,
            @Bind("fileName") String fileName,
            @Bind("fileSize") long fileSize,
            @Bind("operatorId") long operatorUserId,
            @Bind("participantId") long participantUserId,
            @Bind("createdAt") Instant createdAt);

    @SqlUpdate("update file_upload set is_verified = :verified where file_upload_id = :id")
    int updateIsVerified(
            @Bind("id") long fileUploadId,
            @Bind("verified") boolean isVerified);

    @SqlUpdate("update file_upload set uploaded_at = :uploadTime where file_upload_id = :id")
    int updateUploadedAt(
            @Bind("id") long fileUploadId,
            @Bind("uploadTime") Instant uploadTime);

    @SqlUpdate("delete from file_upload where file_upload_id = :id")
    int delete(@Bind("id") long fileUploadId);
}
