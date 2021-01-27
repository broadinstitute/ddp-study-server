package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.broadinstitute.ddp.model.files.FileUploadStatus;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface FileUploadSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into file_upload (file_upload_guid, blob_name, mime_type,"
            + "        file_name, file_size, operator_user_id, participant_user_id,"
            + "        status_id, status_changed_at, created_at, is_replaced)"
            + " values (:guid, :blobName, :mimeType, :fileName, :fileSize, :operatorId, :participantId,"
            + "        (select file_upload_status_id from file_upload_status where file_upload_status_code = :status),"
            + "        :createdAt, :createdAt, false)")
    long insert(
            @Bind("guid") String fileUploadGuid,
            @Bind("blobName") String blobName,
            @Bind("mimeType") String mimeType,
            @Bind("fileName") String fileName,
            @Bind("fileSize") int fileSize,
            @Bind("operatorId") long operatorUserId,
            @Bind("participantId") long participantUserId,
            @Bind("status") FileUploadStatus status,
            @Bind("createdAt") Instant createdAt);
}
