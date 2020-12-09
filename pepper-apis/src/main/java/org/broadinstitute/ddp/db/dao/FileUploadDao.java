package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.fileupload.FileUpload;
import org.broadinstitute.ddp.model.fileupload.FileUploadStatus;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface FileUploadDao extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into file_upload(file_upload_guid, bucket_file_url, url_creation_time, "
            + "file_name, file_size, status) values"
            + " (:fileUploadGuid, :bucketFileUrl, :urlCreationTime, :fileName, :fileSize,"
            + " (select file_upload_status_id from file_upload_status where file_upload_status_name = :status))")
    long insertFileUpload(
            @Bind("fileUploadGuid") String fileUploadGuid,
            @Bind("bucketFileUrl") String bucketFileUrl,
            @Bind("urlCreationTime") Long urlCreationTime,
            @Bind("fileName") String fileName,
            @Bind("fileSize") Long fileSize,
            @Bind("status") FileUploadStatus status);

    @SqlQuery("select fu.file_upload_id, fu.file_upload_guid, fu.bucket_file_url, fu.url_creation_time, "
            + "fu.file_name, fu.file_size, fus.file_upload_status_name as status, fu.file_creation_time "
            + "from file_upload fu join file_upload_status fus on fu.status = fus.file_upload_status_id "
            + "where fu.file_upload_guid = :guid")
    @RegisterConstructorMapper(FileUpload.class)
    Optional<FileUpload> getFileUploadByGuid(@Bind("guid") String guid);

    @SqlUpdate("update file_upload set status = (select file_upload_status_id from file_upload_status where file_upload_status_name = "
            + "'VERIFIED'), file_creation_time = :createTime where file_upload_guid = :guid")
    void setVerified(@Bind("guid") String guid, @Bind("createTime") Long createTime);
}
