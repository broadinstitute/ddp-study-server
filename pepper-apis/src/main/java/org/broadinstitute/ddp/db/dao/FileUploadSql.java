package org.broadinstitute.ddp.db.dao;

import java.time.Instant;

import org.broadinstitute.ddp.model.files.FileScanResult;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface FileUploadSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into file_upload (file_upload_guid, study_id, operator_user_id, participant_user_id,"
            + "        blob_name, mime_type, file_name, file_size, is_verified,"
            + "        created_at, uploaded_at, scanned_at, scan_result_id)"
            + " values (:guid, :studyId, :operatorId, :participantId,"
            + "        :blobName, :mimeType, :fileName, :fileSize, false,"
            + "        :createdAt, null, null, null)")
    long insert(
            @Bind("guid") String fileUploadGuid,
            @Bind("studyId") long studyId,
            @Bind("operatorId") long operatorUserId,
            @Bind("participantId") long participantUserId,
            @Bind("blobName") String blobName,
            @Bind("mimeType") String mimeType,
            @Bind("fileName") String fileName,
            @Bind("fileSize") long fileSize,
            @Bind("createdAt") Instant createdAt);

    @SqlUpdate("update file_upload set is_verified = :verified where file_upload_id = :id")
    int updateIsVerified(
            @Bind("id") long fileUploadId,
            @Bind("verified") boolean isVerified);

    @SqlUpdate("update file_upload set uploaded_at = :uploadedAt, scanned_at = :scannedAt, scan_result_id = ("
            + " select file_scan_result_id from file_scan_result where file_scan_result_code = :scanResult)"
            + "  where file_upload_id = :id")
    int updateStatus(
            @Bind("id") long fileUploadId,
            @Bind("uploadedAt") Instant uploadedAt,
            @Bind("scannedAt") Instant scannedAt,
            @Bind("scanResult") FileScanResult scanResult);

    @SqlUpdate("delete from file_upload where file_upload_id in (<ids>)")
    int bulkDelete(@BindList(value = "ids", onEmpty = EmptyHandling.NULL) Iterable<Long> fileUploadIds);
}
