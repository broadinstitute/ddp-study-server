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
import org.broadinstitute.dsm.service.admin.AdminOperation;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * AdminOperation service to asynchronously export DSM data to ElasticSearch for a set of participants
 */
@Slf4j
public class ElasticExportService implements AdminOperation {
    private DDPInstance ddpInstance;
    private List<String> ddpParticipantIds = new ArrayList<>();
    private boolean verifyElasticData = false;
    private boolean exportAll = false;

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

        if (attributes.containsKey("verifyData")) {
            String force = attributes.get("verifyData");
            if (!StringUtils.isBlank(force)) {
                throw new DSMBadRequestException("Invalid 'verifyData' attribute: not expecting a value");
            }
            verifyElasticData = true;
        }

        // make users be explicit to avoid inadvertently exporting all data by omitting participant IDs
        if (attributes.containsKey("exportAll")) {
            String force = attributes.get("exportAll");
            if (!StringUtils.isBlank(force)) {
                throw new DSMBadRequestException("Invalid 'exportAll' attribute: not expecting a value");
            }
            exportAll = true;
        }

        if (verifyElasticData && exportAll) {
            throw new DSMBadRequestException("Cannot specify both 'verifyData' and 'exportAll' attributes");
        }

        if (!verifyElasticData && !exportAll) {
            if (StringUtils.isBlank(payload)) {
                throw new DSMBadRequestException("No participant IDs provided");
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
        }
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<ExportLog> exportLogs = new ArrayList<>();
        if (verifyElasticData) {

        } else {
            exportParticipants(ddpParticipantIds, ddpInstance.getName(), exportLogs);
        }

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(exportLogs);
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
            log.error("Error exporting participant data to ES: {}", e.toString());
            ExportLog exportLog = new ExportLog("<No entity>");
            exportLog.setError(e.toString());
            // exceptions should be caught lower, so also log this
            log.error("Exception from StudyMigrator.migrateParticipants: {}", e.toString(), e);
        }
    }

    protected static void verifyElasticData(List<String> ddpParticipantIds, String realm,
                                            List<ExportLog> exportLogs) {
        try {
            if (ddpParticipantIds.isEmpty()) {
                StudyMigrator.migrate(realm, exportLogs);
            } else {
                StudyMigrator.migrateParticipants(ddpParticipantIds, realm, exportLogs);
            }
        } catch (Exception e) {
            log.error("Error exporting participant data to ES: {}", e.toString());
            ExportLog exportLog = new ExportLog("<No entity>");
            exportLog.setError(e.toString());
            // exceptions should be caught lower, so also log this
            log.error("Exception from StudyMigrator.migrateParticipants: {}", e.toString(), e);
        }
    }

}
