package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Optional;

import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.files.FileUploadStatus;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface FileUploadDao extends SqlObject {

    @CreateSqlObject
    FileUploadSql getFileUploadSql();

    default FileUpload createAuthorized(String guid, String blobName, String mimeType, String fileName,
                                        int fileSize, long operatorUserId, long participantUserId) {
        Instant now = Instant.now();
        long id = getFileUploadSql().insert(
                guid, blobName, mimeType, fileName, fileSize,
                operatorUserId, participantUserId,
                FileUploadStatus.AUTHORIZED, now);
        return new FileUpload(
                id, guid, blobName, mimeType, fileName, fileSize,
                operatorUserId, participantUserId,
                FileUploadStatus.AUTHORIZED, now, now, false);
    }

    @SqlQuery("select f.*, (select file_upload_status_code from file_upload_status"
            + "       where file_upload_status_id = f.status_id) as status"
            + "  from file_upload as f"
            + " where f.file_upload_id = :id")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> findById(@Bind("id") long fileUploadId);

    @SqlQuery("select f.*, (select file_upload_status_code from file_upload_status"
            + "       where file_upload_status_id = f.status_id) as status"
            + "  from file_upload as f"
            + " where f.file_upload_guid = :guid")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> findByGuid(@Bind("guid") String fileUploadGuid);
}
