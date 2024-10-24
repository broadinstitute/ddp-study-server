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
import com.google.common.annotations.VisibleForTesting;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.jdbi.v3.core.Handle;

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
    private Long somaticDocumentId;

    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private Long ddpInstanceId;

    private String ddpParticipantId;

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private Long participantId;

    @ColumnName(DBConstants.FILE_NAME)
    private String fileName;

    @ColumnName(DBConstants.MIME_TYPE)
    private String mimeType;

    private String bucket;

    private String blobPath;

    @ColumnName(DBConstants.CREATED_BY_USER_ID)
    private Long createdByUserId;

    @ColumnName(DBConstants.CREATED_AT)
    private Long createdAt;

    @ColumnName(DBConstants.DELETED_BY_USER_ID)
    private Long deletedByUserId;

    @ColumnName(DBConstants.DELETED_AT)
    private Long deletedAt;

    @ColumnName(DBConstants.IS_VIRUS_FREE)
    private Boolean isVirusFree;

    @ColumnName(DBConstants.SENT_AT)
    private Long sentAt;

    private static final String SQL_SELECT_DOCUMENTS_BASE = "SELECT sD.id, p.ddp_instance_id, "
            + "p.ddp_participant_id, p.participant_id, sD.file_name, sD.mime_type, sD.bucket, sD.blob_path, "
            + "sD.created_by_user_id, sD.created_at, sD.deleted_by_user_id, sD.deleted_at, sD.is_virus_free, t.created_date  "
            + "FROM  somatic_documents sD "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = sD.ddp_instance_id) "
            + "LEFT JOIN ddp_participant p on (p.participant_id = sD.participant_id) "
            + "LEFT JOIN ddp_survey_trigger t on (t.survey_trigger_id = sD.trigger_id) "
    ;

    private static final String SQL_SELECT_DOCUMENTS_BY_REALM = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE ddp.instance_name = ?";

    private static final String SQL_SELECT_DOCUMENTS_BY_REALM_AND_PTPT = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE ddp.instance_name = ? AND p.ddp_participant_id = ?";

    private static final String SQL_SELECT_DOCUMENT_BY_REALM_PTPT_DOCID = SQL_SELECT_DOCUMENTS_BY_REALM_AND_PTPT
            + " AND sD.id = ?";

    private static final String SQL_SELECT_DOCUMENT_BY_ID_AND_REALM = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE sD.id = ? AND ddp.instance_name = ?";

    private static final String SQL_SELECT_DOCUMENT_BY_BUCKET_AND_PATH = SQL_SELECT_DOCUMENTS_BASE
            + "WHERE sD.bucket = ? AND sD.blob_path = ?";

    private static final String SQL_UPDATE_SENT_AT_TIME = "UPDATE somatic_documents AS sD "
            + "LEFT JOIN ddp_instance as ddp ON (ddp.ddp_instance_id = sD.ddp_instance_id) SET sD.trigger_id = ? "
            + "WHERE sD.id = ? AND ddp.instance_name = ?";

    private static final String SQL_UPDATE_VIRUS_STATUS_SUCCESS = "UPDATE somatic_documents SET is_virus_free = ?, "
            + "bucket = ?, blob_path = ? WHERE bucket = ? AND blob_path = ?";

    private static final String SQL_UPDATE_VIRUS_STATUS_FAILED = "UPDATE somatic_documents SET is_virus_free = ?, "
            + "deleted_at = ? WHERE bucket = ? AND blob_path = ?";

    private static final String SQL_DELETE_DOCUMENT_BY_DOCUMENT_ID_AND_REALM = "UPDATE somatic_documents AS sD "
            + "LEFT JOIN ddp_instance as ddp ON (ddp.ddp_instance_id = sD.ddp_instance_id) SET sD.deleted_by_user_id = ?, "
            + "sD.deleted_at = ? WHERE sD.id = ? AND ddp.instance_name = ?";

    private static final String SQL_INSERT_SOMATIC_DOCUMENT = "INSERT INTO somatic_documents SET ddp_instance_id = "
            + "(SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?), file_name = ?, "
            + "mime_type = ?, bucket = ?, blob_path = ?, created_by_user_id = ?, created_at= ?, "
            + "participant_id = (SELECT p.participant_id from ddp_participant p "
            + "LEFT JOIN ddp_instance as ddp ON (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "WHERE p.ddp_participant_id = ? AND ddp.instance_name= ?)";

    private static final String SQL_HARD_DELETE_SOMATIC_DOCUMENT = "delete from somatic_documents where id = ?";

    public SomaticResultUpload() {}

    public SomaticResultUpload(long ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    /**
     * Builds A Somatic Document for storage
     *
     * @param somaticDocumentId document ID
     * @param ddpInstanceId     Instance ID
     * @param ddpParticipantId  DDP Participant ID
     * @param participantId     Participant ID
     * @param fileName          Name of the file
     * @param mimeType          Mime type of the file
     * @param bucket            The bucket in GCS
     * @param blobPath          Path of the file in GCS
     * @param createdByUserId   User who uploaded the file
     * @param createdAt         Epoch of when the file was uploaded
     * @param deletedByUserId   User who deleted the file
     * @param deletedAt         Epoch of when the file was deleted
     * @param isVirusFree       True when the file has been through file scanning and moved to study bucket.
     * @param sentAt            Epoch of most recent time this file was sent to DSS for delivery to participant
     */
    public SomaticResultUpload(long somaticDocumentId, long ddpInstanceId, String ddpParticipantId, long participantId, String fileName,
                               String mimeType, String bucket, String blobPath, long createdByUserId, long createdAt,
                               long deletedByUserId, long deletedAt, boolean isVirusFree, long sentAt) {
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
        this.sentAt = sentAt;
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
            throw new DsmInternalError("Couldn't get list of Somatic File Uploads");
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
            throw new DsmInternalError("Error getting somatic document list entries ", results.resultException);
        }
        return documents;
    }

    public static SomaticResultUpload getSomaticFileUploadByIdAndRealm(Long documentId, String realm) {
        List<SomaticResultUpload> documents = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENT_BY_ID_AND_REALM)) {
                stmt.setLong(1, documentId);
                stmt.setString(2, realm);
                runSelect(documents, stmt);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        return getSingleSomaticResultUpload(documentId, documents, results);
    }

    public static SomaticResultUpload getSomaticFileUploadByIdRealmPTPT(long documentId, String realm, String ddpParticipantId) {
        List<SomaticResultUpload> documents = new ArrayList<>();
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DOCUMENT_BY_REALM_PTPT_DOCID)) {
                stmt.setString(1, realm);
                stmt.setString(2, ddpParticipantId);
                stmt.setLong(3, documentId);
                runSelect(documents, stmt);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        return getSingleSomaticResultUpload(documentId, documents, results);
    }

    private static SomaticResultUpload getSingleSomaticResultUpload(long documentId, List<SomaticResultUpload> documents,
                                                                    SimpleResult results) {
        if (results.resultException != null) {
            throw new DsmInternalError("Error getting somatic document entry ", results.resultException);
        }
        if (documents.size() > 1) {
            throw new DsmInternalError("Error getting somatic file!  More than one document found for id " + documentId);
        }
        if (documents.isEmpty()) {
            throw new DSMBadRequestException("Bad request for document with id " + documentId);
        }
        if (documents.get(0) == null) {
            throw new DsmInternalError("somatic upload document is null for " + documentId);
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
            throw new DsmInternalError("Error getting somatic document entry ", results.resultException);
        }
        if (documents.size() > 1) {
            throw new DsmInternalError(
                    String.format("Error getting somatic file!  More than one document found for bucket %s and blobPath %s",
                            bucket, blobPath));
        }
        if (documents.isEmpty()) {
            throw new DsmInternalError(String.format("Bad request for document with %s %s", bucket, blobPath));
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
            rs.getBoolean(DBConstants.IS_VIRUS_FREE),
            rs.getLong(DBConstants.CREATED_DATE));
    }

    /**
     * Only use this for testing.  Hard deletes the row.  For production code,
     * we only soft delete, so please use other delete methods for normal operations.
     */
    @VisibleForTesting
    public static void hardDeleteSomaticDocumentById(Handle handle, long id) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(SQL_HARD_DELETE_SOMATIC_DOCUMENT)) {
            stmt.setLong(1, id);
            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted != 1) {
                throw new DsmInternalError("Deleted " + rowsDeleted + " rows for somatic document " + id);
            }
        } catch (SQLException e) {
            throw new DsmInternalError("Error deleting somatic document " + id, e);
        }
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
                            dbVals.resultValue = rs.getLong(1);
                        }
                    } catch (Exception e) {
                        throw new DsmInternalError("Error inserting somatic file", e);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error adding new file for participantId w/ id " + ddpParticipantId,
                    results.resultException);
        }
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadByIdAndRealm((long)results.resultValue, realm);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.DOC_ID,
                Exportable.getParticipantGuid(ddpParticipantId, ddpInstance.getParticipantIndexES()), ddpInstanceDto);

        return somaticResultUpload;
    }

    public static boolean updateSuccessfulVirusScanningResult(String bucket, String blobPath, String newBucket, String newBlobPath) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult  dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VIRUS_STATUS_SUCCESS)) {
                stmt.setBoolean(1, true);
                stmt.setString(2, newBucket);
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
            throw new DsmInternalError("Error updating file status when file was clean", results.resultException);
        }
        return getResultAndUpdateElastic(newBucket, newBlobPath);
    }

    private static boolean getResultAndUpdateElastic(String newBucket, String newBlobPath) {
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadByBucketAndBlobPath(newBucket, newBlobPath);
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
            throw new DsmInternalError("Error updating file status when virus was found", results.resultException);
        }
        return getResultAndUpdateElastic(bucket, blobPath);
    }

    public static SomaticResultUpload updateTriggerId(long id, long triggerId, String realm) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_SENT_AT_TIME)) {
                stmt.setLong(1, triggerId);
                stmt.setLong(2, id);
                stmt.setString(3, realm);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error setting trigger_id for somatic document", results.resultException);
        }
        return updateElasticEntry(id, realm);
    }

    private static SomaticResultUpload updateElasticEntry(long id, String realm) {
        SomaticResultUpload somaticResultUpload = getSomaticFileUploadByIdAndRealm(id, realm);
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(Math.toIntExact(somaticResultUpload.ddpInstanceId));
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(ddpInstance.getDdpInstanceId())).orElseThrow();
        writeToElastic(somaticResultUpload, ESObjectConstants.SOMATIC_DOCUMENT_ID,
                somaticResultUpload.getSomaticDocumentId(), ddpInstanceDto);
        return somaticResultUpload;
    }

    public static SomaticResultUpload deleteDocumentByDocumentIdAndRealm(long deletingUserId, long documentId, String realm) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DOCUMENT_BY_DOCUMENT_ID_AND_REALM)) {
                stmt.setLong(1, deletingUserId);
                stmt.setLong(2, Instant.now().getEpochSecond());
                stmt.setLong(3, documentId);
                stmt.setString(4, realm);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error updating deleted file status", results.resultException);
        }

        return updateElasticEntry(documentId, realm);
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
            throw new DsmInternalError("Error updating the information in the document database, contact a DSM Developer.");
        }
    }

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.ofNullable(getDdpInstanceId());
    }
}
