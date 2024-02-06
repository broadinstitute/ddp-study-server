package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.migration.StudyMigrator;
import org.broadinstitute.dsm.service.admin.AdminOperation;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.util.ParticipantUtil;

@Slf4j
public class ElasticExportService implements AdminOperation {
    private static final Gson gson = new Gson();
    private DDPInstance ddpInstance;
    private List<String> ddpParticipantIds = new ArrayList<>();

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
        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("No participant IDs provided");
        }

        ParticipantListRequest req = ParticipantListRequest.fromJson(payload);
        ddpParticipantIds = req.getParticipants();
        List<String> badIds = new ArrayList<>();
        for (String ddpParticipantId: ddpParticipantIds) {
            if (!ParticipantUtil.isGuid(ddpParticipantId)) {
                badIds.add(ddpParticipantId);
            }
        }
        if (!badIds.isEmpty()) {
            throw new DSMBadRequestException("Invalid participant IDs: " + String.join(", ", badIds));
        }
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<ExportLog> exportLogs = new ArrayList<>();
        exportParticipants(ddpParticipantIds, ddpInstance.getName(), exportLogs);

        // update job log record
        try {
            String json = gson.toJson(exportLogs);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED,
                    json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString(), e);
        }
    }

    protected static void exportParticipants(List<String> ddpParticipantIds, String realm,
                                             List<ExportLog> exportLogs) {
        try {
            // export participant data to ES
            StudyMigrator.migrateParticipants(ddpParticipantIds, realm, exportLogs);
        } catch (Exception e) {
            log.error("Error migrating participant data to ES: {}", e.toString());
            ExportLog exportLog = new ExportLog("<No entity>");
            exportLog.setError(e.toString());
            // exceptions should be caught lower, so also log this
            log.error("Exception from StudyMigrator.migrateParticipants: {}", e.toString(), e);
        }
    }
}
