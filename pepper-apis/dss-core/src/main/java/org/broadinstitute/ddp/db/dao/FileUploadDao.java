package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface FileUploadDao extends SqlObject {

    @CreateSqlObject
    FileUploadSql getFileUploadSql();

    default FileUpload createAuthorized(String guid, long studyId, long operatorUserId, long participantUserId,
                                        String blobName, String mimeType, String fileName, long fileSize) {
        Instant now = Instant.now();
        long id = getFileUploadSql().insert(
                guid, studyId, operatorUserId, participantUserId,
                blobName, mimeType, fileName, fileSize, now);

        return FileUpload.builder()
                .id(id)
                .guid(guid)
                .studyId(studyId)
                .operatorUserId(operatorUserId)
                .participantUserId(participantUserId)
                .blobName(blobName)
                .mimeType(mimeType)
                .fileName(fileName)
                .fileSize(fileSize)
                .isVerified(false)
                .createdAt(now)
                .build();
    }

    default void markVerified(long fileUploadId) {
        DBUtils.checkUpdate(1, getFileUploadSql().updateIsVerified(fileUploadId, true));
    }

    default void updateStatus(long fileUploadId, Instant uploadedAt, Instant scannedAt, FileScanResult scanResult) {
        DBUtils.checkUpdate(1, getFileUploadSql().updateStatus(fileUploadId, uploadedAt, scannedAt, scanResult));
    }

    default void deleteById(long fileUploadId) {
        deleteByIds(Set.of(fileUploadId));
    }

    default void deleteByIds(Collection<Long> fileUploadIds) {
        if (fileUploadIds != null && !fileUploadIds.isEmpty()) {
            DBUtils.checkDelete(fileUploadIds.size(), getFileUploadSql().bulkDelete(fileUploadIds));
        }
    }

    @SqlUpdate("delete from file_upload where participant_user_id = :userId or operator_user_id = :userId")
    void deleteByParticipantOrOperatorId(@Bind("userId") long userId);

    @SqlQuery("SELECT f.file_upload_id AS file_upload_id, "
            + "     f.file_upload_guid AS file_upload_guid, "
            + "     f.study_id AS study_id, "
            + "     f.operator_user_id AS operator_user_id, "
            + "     f.participant_user_id AS participant_user_id, "
            + "     f.blob_name AS blob_name, "
            + "     f.mime_type AS mime_type, "
            + "     f.file_name AS file_name, "
            + "     f.file_size AS file_size, "
            + "     f.is_verified AS is_verified, "
            + "     f.created_at AS created_at, "
            + "     f.uploaded_at AS uploaded_at, "
            + "     f.scanned_at AS scanned_at, "
            + "     f.notification_sent_at AS notification_sent_at, "
            + "     (SELECT file_scan_result_code "
            + "         FROM file_scan_result "
            + "         WHERE file_scan_result_id = f.scan_result_id) AS scan_result "
            + "FROM file_upload AS f "
            + "WHERE f.file_upload_id IN (<uploadIds>)")
    @RegisterConstructorMapper(FileUpload.class)
    List<FileUpload> findByIds(@BindList("uploadIds") long... uploadIds);

    default Optional<FileUpload> findById(long fileUploadId) {
        return findByIds(fileUploadId).stream().findFirst();
    }

    @SqlQuery("select f.*, (select file_scan_result_code from file_scan_result"
            + "       where file_scan_result_id = f.scan_result_id) as scan_result"
            + "  from file_upload as f"
            + " where f.file_upload_guid = :guid")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> findByGuid(@Bind("guid") String fileUploadGuid);

    @SqlQuery("select f.*, (select file_scan_result_code from file_scan_result"
            + "       where file_scan_result_id = f.scan_result_id) as scan_result"
            + "  from file_upload as f"
            + " where f.file_upload_id = :id for update")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> findAndLockById(@Bind("id") long fileUploadId);

    @SqlQuery("select f.*, (select file_scan_result_code from file_scan_result"
            + "       where file_scan_result_id = f.scan_result_id) as scan_result"
            + "  from file_upload as f"
            + " where f.file_upload_guid = :guid for update")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> findAndLockByGuid(@Bind("guid") String fileUploadGuid);

    @SqlQuery("select f.*, (select file_scan_result_code from file_scan_result"
            + "       where file_scan_result_id = f.scan_result_id) as scan_result"
            + "  from file_upload as f"
            + " where f.created_at < :ts"
            + "   and (f.is_verified is false or f.file_upload_id not in (select file_upload_id from file_answer))"
            + " order by f.file_upload_id limit :limit offset :offset")
    @RegisterConstructorMapper(FileUpload.class)
    Stream<FileUpload> findUnverifiedOrUnassociatedUploads(
            @Bind("ts") Instant olderThanTimestamp,
            @Bind("offset") int offset,
            @Bind("limit") int limit);

    @SqlQuery("select f.*, (select file_scan_result_code from file_scan_result"
            + "       where file_scan_result_id = f.scan_result_id) as scan_result"
            + "  from file_upload as f"
            + " where f.is_verified is true"
            + "   and f.file_upload_id in (select file_upload_id from file_answer)"
            + "   and f.participant_user_id in (<userIds>)"
            + "   and f.study_id = :studyId"
            + " order by f.participant_user_id, f.file_upload_id")
    @RegisterConstructorMapper(FileUpload.class)
    Stream<FileUpload> findVerifiedAndAssociatedUploadsForParticipants(
            @Bind("studyId") long studyId,
            @BindList(value = "userIds", onEmpty = EmptyHandling.NULL) Iterable<Long> participantUserIds);

    @SqlQuery("select f.* "
            + "  from file_upload as f "
            + " where f.notification_sent_at IS NULL")
    @RegisterConstructorMapper(FileUpload.class)
    Stream<FileUpload> findWithoutSentNotification();

    @SqlUpdate("update file_upload f "
            + "    set f.notification_sent_at = NOW() "
            + "  where f.notification_sent_at IS NULL AND f.study_id = :studyId")
    void setNotificationSentByStudyId(@Bind("studyId") long studyId);

    @SqlUpdate("update file_upload f "
            + "    set f.notification_sent_at = NOW() "
            + "  where f.notification_sent_at IS NULL AND f.file_upload_id in (<fileUploadIds>)")
    void setNotificationSentByFileUploadIds(@BindList(value = "fileUploadIds", onEmpty = EmptyHandling.NULL) Iterable<Long> fileUploadIds);

    @SqlQuery("select file_upload_id, file_upload_guid, file_name, file_size"
            + "  from file_upload where file_upload_guid = :guid")
    @RegisterConstructorMapper(FileInfo.class)
    Optional<FileInfo> findFileInfoByGuid(@Bind("guid") String fileUploadGuid);
}
