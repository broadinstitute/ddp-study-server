package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
@Data
@TableName(name = DBConstants.SOMATIC_DOCUMENTS_TABLE,
        alias = DBConstants.SOMATIC_DOCUMENTS_TABLE_ALIAS,
        primaryKey = DBConstants.SOMATIC_DOCUMENTS_PK,
        columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SomaticResultUpload implements HasDdpInstanceId {
    @ColumnName(ESObjectConstants.SOMATIC_DOCUMENT_ID)
    private final Long somaticDocumentId;

    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private final Long ddpInstanceId;

    private final String ddpParticipantId;

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private final Long participantId;

    @ColumnName(DBConstants.FILE_NAME)
    private final String fileName;

    @ColumnName(DBConstants.MIME_TYPE)
    private final String mimeType;

    private final String bucket;

    private final String blobPath;

    @ColumnName(DBConstants.CREATED_BY_USER_ID)
    private final Long createdByUserId;

    @ColumnName(DBConstants.CREATED_AT)
    private final long createdAt;

    @ColumnName(DBConstants.DELETED_BY_USER_ID)
    private final Long deletedByUserId;

    @ColumnName(DBConstants.DELETED_AT)
    private final long deletedAt;

    @ColumnName(DBConstants.IS_VIRUS_FREE)
    private Boolean isVirusFree;

    private static final String SQL_SELECT_DOCUMENTS_BASE = "SELECT sD.id, p.ddp_instance_id, "
            + "p.ddp_participant_id, p.participant_id, sD.file_name, sD.mime_type, sD.bucket, sD.blob_path, "
            + "sD.created_by_user_id, sD.created_at, sD.deleted_by_user_id, sD.deleted_at, sD.is_virus_free "
            + "FROM  somatic_documents sD "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = sD.ddp_instance_id) "
            + "LEFT JOIN ddp_participant p on (p.participant_id = sD.participant_id) ";

    private static final String SQL_SELECT_DOCUMENTS_BY_REALM = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE ddp.instance_name = ?";

    private static final String SQL_SELECT_DOCUMENTS_BY_REALM_AND_PTPT = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE ddp.instance_name = ? AND ddp_participant_id = ?";

    private static final String SQL_SELECT_DOCUMENT_BY_ID = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE sD.id = ?";

    private static final String SQL_SELECT_DOCUMENT_BY_BUCKET_AND_PATH = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE sD.bucket = ? AND sD.blob_path = ?";

    private static final String SQL_UPDATE_VIRUS_STATUS_SUCCESS = "UPDATE somatic_documents SET is_virus_free = ?, "
            + "bucket = ?, blob_path = ? WHERE bucket = ? AND blob_path = ?";

    private static final String SQL_UPDATE_VIRUS_STATUS_FAILED = "UPDATE somatic_documents SET is_virus_free = ?, "
            + "deleted_at = ? WHERE bucket = ? AND blob_path = ?";

    private static final String SQL_DELETE_DOCUMENT_BY_DOCUMENT_ID = "UPDATE somatic_documents SET deleted_by_user_id = ?, "
            + "deleted_at = ? WHERE id = ?";

    private static final String SQL_INSERT_SOMATIC_DOCUMENT = "INSERT INTO somatic_documents SET ddp_instance_id = "
            + "(SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?), file_name = ?, "
            + "mime_type = ?, bucket = ?, blob_path = ?, created_by_user_id = ?, created_at= ?, "
            + "participant_id = (SELECT p.participant_id from ddp_participant p "
            + "LEFT JOIN ddp_instance as ddp ON (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "WHERE p.ddp_participant_id = ? AND ddp.instance_name= ?)";

    /**
     * Builds A Somatic Document for storage
     *
     * @param somaticDocumentId               document ID
     * @param ddpInstanceId    Instance ID
     * @param ddpParticipantId DDP Participant ID
     * @param participantId    Participant ID
     * @param fileName         Name of the file
     * @param mimeType         Mime type of the file
     * @param bucket           The bucket in GCS
     * @param blobPath         Path of the file in GCS
     * @param createdByUserId  User who uploaded the file
     * @param createdAt        Epoch of when the file was uploaded
     * @param deletedByUserId  User who deleted the file
     * @param deletedAt        Epoch of when the file was deleted
     * @param isVirusFree       True when the file has been through file scanning and moved to study bucket.
     */
    public SomaticResultUpload(Long somaticDocumentId, long ddpInstanceId, String ddpParticipantId, long participantId, String fileName,
                               String mimeType, String bucket, String blobPath, long createdByUserId, long createdAt,
                               long deletedByUserId, long deletedAt, boolean isVirusFree) {
        this.somaticDocumentId = somaticDocumentId;
        this.ddpInstanceId = ddpInstanceId;
        this.ddpParticipantId = ddpParticipantId;
        this.participantId = participantId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.bucket = bucket;
        this.blobPath = blobPath;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.deletedByUserId = deletedByUserId;
        this.deletedAt = deletedAt;
        this.isVirusFree = isVirusFree;
    }

    public static Map<String, List<SomaticResultUpload>> getSomaticFileUploadDocuments(String realm) {
        Map<String, List<SomaticResultUpload>> resultsMap = new HashMap<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENTS_BY_REALM)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        resultsMap.computeIfAbsent(ddpParticipantId, k -> new ArrayList<>()).add(getResult(rs));
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of Somatic File Uploads");
        }

        return resultsMap;
    }

    public static List<SomaticResultUpload> getSomaticFileUploadDocuments(String realm, String ddpParticipantId) {
        List<SomaticResultUpload> documents = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENTS_BY_REALM_AND_PTPT)) {
                stmt.setString(1, realm);
                stmt.setString(2, ddpParticipantId);
                runSelect(documents, stmt);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting somatic document list entries ", results.resultException);
        }
        return documents;
    }

    public static SomaticResultUpload getSomaticFileUploadById(int documentId) {
        List<SomaticResultUpload> documents = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENT_BY_ID)) {
                stmt.setInt(1, documentId);
                runSelect(documents, stmt);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting somatic document entry ", results.resultException);
        }
        if (documents.size() > 1) {
            throw new RuntimeException("Error getting somatic file!  More than one document found for id " + documentId);
        }
        if (documents.size() < 1) {
            throw new RuntimeException("Bad request for document with id " + documentId);
        }
        return documents.get(0);
    }

    public static SomaticResultUpload getSomaticFileUploadByBucketAndBlobPath(String bucket, String blobPath) {
        List<SomaticResultUpload> documents = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENT_BY_BUCKET_AND_PATH)) {
                stmt.setString(1, bucket);
                stmt.setString(2, blobPath);
                runSelect(documents, stmt);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting somatic document entry ", results.resultException);
        }
        if (documents.size() > 1) {
            throw new RuntimeException(
                    String.format("Error getting somatic file!  More than one document found for bucket %s and blobPath %s",
                            bucket, blobPath));
        }
        if (documents.isEmpty()) {
            throw new RuntimeException(String.format("Bad request for document with %s %s", bucket, blobPath));
        }
        return documents.get(0);
    }

    private static void runSelect(List<SomaticResultUpload> documents, PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                documents.add(getResult(rs));
            }
        }
    }

    private static SomaticResultUpload getResult(ResultSet rs) throws SQLException {
        return new SomaticResultUpload(
            rs.getLong(DBConstants.SOMATIC_DOCUMENTS_PK),
            rs.getInt(DBConstants.DDP_INSTANCE_ID),
            rs.getString(DBConstants.DDP_PARTICIPANT_ID),
            rs.getLong(DBConstants.PARTICIPANT_ID),
            rs.getString(DBConstants.FILE_NAME),
            rs.getString(DBConstants.MIME_TYPE),
            rs.getString(DBConstants.BUCKET),
            rs.getString(DBConstants.BLOB_PATH),
            rs.getLong(DBConstants.CREATED_BY_USER_ID),
            rs.getLong(DBConstants.CREATED_AT),
            rs.getLong(DBConstants.DELETED_BY_USER_ID),
            rs.getLong(DBConstants.DELETED_AT),
            rs.getBoolean((DBConstants.IS_VIRUS_FREE)));
    }

    public static SomaticResultUpload createFileUpload(String realm, String ddpParticipantId, String fileName,
                                                       String mimeType, String bucket, String blobPath, long createdByUserId) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SOMATIC_DOCUMENT, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, realm);
                stmt.setString(2, fileName);
                stmt.setString(3, mimeType);
                stmt.setString(4, bucket);
                stmt.setString(5, blobPath);
                stmt.setLong(6, createdByUserId);
                stmt.setLong(7, Instant.now().getEpochSecond());
                stmt.setString(8, ddpParticipantId);
                stmt.setString(9, realm);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            dbVals.resultValue = rs.getInt(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error inserting somatic file", e);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new file for participantId w/ id " + ddpParticipantId,
                    results.resultException);
        }
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadById((int)results.resultValue);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.DOC_ID,
                Exportable.getParticipantGuid(ddpParticipantId, ddpInstance.getParticipantIndexES()), ddpInstanceDto);

        return somaticResultUpload;
    }

    public static boolean updateSuccessfulVirusScanningResult(String bucket, String blobPath, String newbucket, String newBlobPath) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult  dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VIRUS_STATUS_SUCCESS)) {
                stmt.setBoolean(1, true);
                stmt.setString(2, newbucket);
                stmt.setString(3, newBlobPath);
                stmt.setString(4, bucket);
                stmt.setString(5, blobPath);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating file status when file was clean", results.resultException);
        }
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadByBucketAndBlobPath(newbucket, newBlobPath);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.SOMATIC_DOCUMENT_ID,
                somaticResultUpload.getSomaticDocumentId(), ddpInstanceDto);
        return true;
    }

    public static boolean updateUnsuccessfulVirusScanningResult(String bucket, String blobPath) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult  dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VIRUS_STATUS_FAILED)) {
                stmt.setBoolean(1, false);
                stmt.setLong(2, Instant.now().getEpochSecond());
                stmt.setString(3, bucket);
                stmt.setString(4, blobPath);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating file status when virus was found", results.resultException);
        }
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadByBucketAndBlobPath(bucket, blobPath);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.SOMATIC_DOCUMENT_ID,
                somaticResultUpload.getSomaticDocumentId(), ddpInstanceDto);
        return true;
    }

    public static SomaticResultUpload deleteDocumentByDocumentId(long deletingUserId, int documentId) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DOCUMENT_BY_DOCUMENT_ID)) {
                stmt.setLong(1, deletingUserId);
                stmt.setLong(2, Instant.now().getEpochSecond());
                stmt.setInt(3, documentId);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating deleted file status", results.resultException);
        }

        SomaticResultUpload somaticResultUpload = getSomaticFileUploadById(documentId);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.SOMATIC_DOCUMENT_ID,
                somaticResultUpload.getSomaticDocumentId(), ddpInstanceDto);
        return somaticResultUpload;
    }

    private static void writeToElastic(SomaticResultUpload somaticResultUpload, String key, Object resultValue,
                                       DDPInstanceDto ddpInstanceDto) {
        try {
            UpsertPainlessFacade.of(DBConstants.SOMATIC_DOCUMENTS_TABLE_ALIAS, somaticResultUpload, ddpInstanceDto,
                    ESObjectConstants.SOMATIC_DOCUMENT_ID, key, resultValue,
                    new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            log.error(String.format("Error updating somatic document id {} in ElasticSearch: %s",
                    somaticResultUpload.getSomaticDocumentId()));
            e.printStackTrace();
        }
    }

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.ofNullable(getDdpInstanceId());
    }
}
