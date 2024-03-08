package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.migration.StudyMigrator;
import org.broadinstitute.dsm.model.elastic.migration.VerificationLog;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * AdminOperation service to asynchronously export DSM data to ElasticSearch or verify data in ElasticSearch
 * for a given realm.
 * </p>
 * To export some participants, provide a list of participant IDs as the request payload.
 * To export all participants, specify the 'exportAll' attribute. (This is a safety measure to avoid accidentally
 * exporting all data if the user forgets to provide participant IDs.)
 * To verify data in ElasticSearch, specify the 'verifyData' attribute. If a list of participant IDs is provided
 * only those participant records are verified, otherwise all participant records for the realm are verified.
 */
@Slf4j
public class ElasticExportService extends ParticipantAdminOperationService {
    private DDPInstance ddpInstance;
    private List<String> ddpParticipantIds = new ArrayList<>();
    private boolean verifyElasticData = false;
    private boolean verifyFields = false;

    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId ID of user performing operation
     * @param realm export realm
     * @param attributes currently unused
     * @param payload request body as ParticipantListRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        ddpInstance = DDPInstance.getDDPInstance(realm);
        if (ddpInstance == null) {
            throw new DsmInternalError("Invalid realm: " + realm);
        }
        String esIndex = ddpInstance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + realm);
        }

        verifyElasticData = isBooleanProperty("verifyData", attributes);
        verifyFields = isBooleanProperty("verifyFields", attributes);
        if (verifyFields && !verifyElasticData) {
            throw new DSMBadRequestException("'verifyFields attribute' is only valid with 'verifyData' attribute");
        }

        // make users be explicit to avoid inadvertently exporting all data by omitting participant IDs
        boolean exportAll = isBooleanProperty("exportAll", attributes);
        if (verifyElasticData && exportAll) {
            throw new DSMBadRequestException("Cannot specify both 'verifyData' and 'exportAll' attributes");
        }

        if (StringUtils.isNotBlank(payload)) {
            if (exportAll) {
                throw new DSMBadRequestException("Cannot specify both 'exportAll' and participant IDs");
            }
            ParticipantListRequest req = ParticipantListRequest.fromJson(payload);
            ddpParticipantIds = req.getParticipants();
            List<String> badIds = new ArrayList<>();
            for (String ddpParticipantId : ddpParticipantIds) {
                if (!ParticipantUtil.isGuid(ddpParticipantId)) {
                    badIds.add(ddpParticipantId);
                }
            }
            if (!badIds.isEmpty()) {
                throw new DSMBadRequestException("Invalid participant IDs: " + String.join(", ", badIds));
            }
            if (ddpParticipantIds.isEmpty()) {
                throw new DSMBadRequestException("No valid participant IDs provided");
            }
        } else if (!exportAll && !verifyElasticData) {
            throw new DSMBadRequestException("No participant IDs provided. "
                    + "Use 'exportAll' to export all participants.");
        }
    }

    /**
     * Run the asynchronous part of export operation, updating the AdminRecord with operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        if (verifyElasticData) {
            runVerification(operationId);
            return;
        }

        List<ExportLog> exportLogs = new ArrayList<>();
        exportParticipants(ddpParticipantIds, ddpInstance.getName(), exportLogs);

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(exportLogs);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED,
                    json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString(), e);
        }
    }

    private void runVerification(int operationId) {
        List<VerificationLog> verificationLogs = verifyElasticData(ddpParticipantIds, ddpInstance, verifyFields);
        // unless verifying for specific participants, strip out the successful verifications since they bloat
        // the logs and don't add much value at large scales
        if (ddpParticipantIds.isEmpty()) {
            verificationLogs.removeIf(log -> log.getStatus().equals(VerificationLog.VerificationStatus.VERIFIED));
        }

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(verificationLogs);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED,
                    json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString(), e);
        }
    }

    protected static void exportParticipants(List<String> ddpParticipantIds, String realm,
                                             List<ExportLog> exportLogs) {
        try {
            if (ddpParticipantIds.isEmpty()) {
                StudyMigrator.migrate(realm, exportLogs);
            } else {
                StudyMigrator.migrateParticipants(ddpParticipantIds, realm, exportLogs);
            }
        } catch (Exception e) {
            ExportLog exportLog = new ExportLog("<No entity>");
            exportLog.setError(e.toString());
            // exceptions should be caught lower, so also log this
            log.error("Exception from StudyMigrator.migrateParticipants: {}", e.toString(), e);
        }
    }

    /**
     * Verify DSM ES data for a realm/index/instance
     *
     * @param ddpParticipantIds list of participant IDs to verify for (if empty, verify all participants)
     * @param verifyFields true if differences in record fields should be logged
     */
    protected static List<VerificationLog> verifyElasticData(List<String> ddpParticipantIds,
                                                             DDPInstance ddpInstance, boolean verifyFields) {
        String index = ddpInstance.getParticipantIndexES();
        ElasticSearchService esService = new ElasticSearchService();
        try {
            Map<String, Map<String, Object>> dsmObjectMap = esService.getAllDsmData(index);
            if (!ddpParticipantIds.isEmpty()) {
                dsmObjectMap.keySet().retainAll(ddpParticipantIds);
            }
            return StudyMigrator.verifyParticipants(ddpParticipantIds, dsmObjectMap, ddpInstance, verifyFields);
        } catch (Exception e) {
            VerificationLog verificationLog = new VerificationLog("<No participant>", "<No entity>");
            verificationLog.setError(e.toString());
            // exceptions should be caught lower, so also log this
            log.error("Exception from StudyMigrator.migrateParticipants: {}", e.toString(), e);
            return List.of(verificationLog);
        }
    }
}
