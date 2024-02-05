package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;
import org.broadinstitute.dsm.service.admin.AdminOperation;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;

/**
 * AdminOperation service to asynchronously fix various ParticipantData issues
 */
@Slf4j
public class ParticipantDataFixupService implements AdminOperation {

    protected enum UpdateStatus {
        UPDATED,
        NOT_UPDATED,
        ERROR,
        NO_PARTICIPANT_DATA
    }

    private static final Gson gson = new Gson();
    protected List<String> validRealms = List.of("atcp");
    private DDPInstance ddpInstance;
    private Map<String, List<ParticipantData>> participantDataByPtpId;

    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId     ID of user performing operation
     * @param realm      realm for fixup
     * @param attributes unused
     * @param payload    request body, if any, as ParticipantDataFixupRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        // this class currently only supports AT realm
        if (!validRealms.contains(realm.toLowerCase())) {
            throw new DsmInternalError("Invalid realm for ParticipantDataFixupService: " + realm);
        }

        String fixupType = attributes.get("fixupType");
        if (StringUtils.isBlank(fixupType)) {
            throw new DSMBadRequestException("Missing required attribute 'fixupType'");
        }

        // for now, this is the only fixup available
        if (!fixupType.equalsIgnoreCase("atcpGenomicId")) {
            throw new DSMBadRequestException("Invalid fixupType: " + fixupType);
        }

        ddpInstance = DDPInstance.getDDPInstance(realm);
        if (ddpInstance == null) {
            throw new DsmInternalError("Invalid realm: " + realm);
        }
        if (StringUtils.isEmpty(ddpInstance.getParticipantIndexES())) {
            throw new DsmInternalError("No ES participant index for study " + realm);
        }

        ParticipantDataDao dataDao = new ParticipantDataDao();
        participantDataByPtpId = new HashMap<>();

        // handle optional list of ptps
        if (!StringUtils.isBlank(payload)) {
            ParticipantListRequest req = ParticipantListRequest.fromJson(payload);
            List<String> participants = req.getParticipants();
            for (String participantId: participants) {
                List<ParticipantData> ptpData = dataDao.getParticipantDataByParticipantId(participantId);
                if (ptpData.isEmpty()) {
                    throw new DSMBadRequestException("Invalid participant ID: " + participantId);
                }
                participantDataByPtpId.put(participantId, ptpData);
            }
        } else {
            throw new DSMBadRequestException("Missing required payload");
            /* TODO: it feels too easy to inadvertently forget to include the payload, so for now we will require it.
            // Keeping the code in case that becomes untenable -DC
            // get study participants and their data
            // Implementation note: we can either gather all the ptp data now (fewer queries, larger memory footprint),
            // or just gather the ptp IDs here and query for ptp data separately (more queries, smaller memory footprint)
            // At the time of implementation ptps per study were reasonably small (~100) and ptp data size was not large
            List<ParticipantData> dataList =
                    dataDao.getParticipantDataByInstanceId(ddpInstance.getDdpInstanceIdAsInt());
            participantDataByPtpId = dataList.stream().collect(Collectors.groupingBy(ParticipantData::getRequiredDdpParticipantId));
            */
        }
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        // for each participant attempt an update and log results
        for (var entry: participantDataByPtpId.entrySet()) {
            updateLog.add(updateParticipant(entry.getKey(), entry.getValue()));
        }

        // update job log record
        try {
            String json = gson.toJson(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected UpdateLog updateParticipant(String ddpParticipantId, List<ParticipantData> participantDataList) {
        if (participantDataList.isEmpty()) {
            return new UpdateLog(ddpParticipantId, UpdateStatus.NO_PARTICIPANT_DATA.name());
        }
        // no duplicates
        if (participantDataList.size() == 1) {
            return new UpdateLog(ddpParticipantId, UpdateStatus.NOT_UPDATED.name());
        }
        log.info("TEMP: participantDataList size: {}", participantDataList.size());

        try {
            Set<Integer> genomeIdToDelete =
                    getRecordsToDelete(participantDataList, ATDefaultValues.GENOME_STUDY_FIELD_TYPE);
            Set<Integer> exitToDelete =
                    getRecordsToDelete(participantDataList, ATDefaultValues.AT_PARTICIPANT_EXIT);
            log.info("Found {} genomic IDs and {} exit statuses to delete for participant {}",
                    genomeIdToDelete.size(), exitToDelete.size(), ddpParticipantId);

            if (genomeIdToDelete.isEmpty() && exitToDelete.isEmpty()) {
                return new UpdateLog(ddpParticipantId, UpdateStatus.NOT_UPDATED.name());
            }

            // remove records from DB
            deleteParticipantData(genomeIdToDelete);
            deleteParticipantData(exitToDelete);

            // update ES
            Map<Integer, ParticipantData> idToData = participantDataList.stream()
                    .collect(Collectors.toMap(ParticipantData::getParticipantDataId, pd -> pd));
            updateParticipantDataList(idToData, genomeIdToDelete);
            updateParticipantDataList(idToData, exitToDelete);
            WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId,
                    new ArrayList<>(idToData.values()), ddpInstance);
        } catch (Exception e) {
            String msg = String.format("Exception in ParticipantDataFixupService.run for participant %s: %s",
                    ddpParticipantId, e);
            // many of these exceptions will require investigation, but conservatively we will just log
            // at error level for those that are definitely concerning
            if (e instanceof DsmInternalError || e instanceof RuntimeException) {
                log.error(msg);
                e.printStackTrace();
            } else {
                log.warn(msg);
            }
            return new UpdateLog(ddpParticipantId, UpdateStatus.ERROR.name(), e.toString());
        }
        return new UpdateLog(ddpParticipantId, UpdateStatus.UPDATED.name());
    }

    private Set<Integer> getRecordsToDelete(List<ParticipantData> participantDataList, String fieldTypeId) {
        // find all records with the given field type
        Map<Integer, ParticipantData> candidateRecords = participantDataList.stream()
                .filter(participantData ->
                        participantData.getRequiredFieldTypeId().equals(fieldTypeId))
                .collect(Collectors.toMap(ParticipantData::getParticipantDataId, pd -> pd));
        if (candidateRecords.size() <= 1) {
            return Collections.emptySet();
        }

        // multiple, find the one with the lowest ID (assigned sequentially, so chronologically correct, at least for
        // the purposes of this fixup)
        int min = candidateRecords.keySet().stream().min(Comparator.comparing(id -> id)).orElseThrow();
        candidateRecords.remove(min);

        // we are expecting the records to delete to have only one data map entry
        candidateRecords.values().forEach(pd -> {
            if (pd.getDataMap().size() > 1) {
                throw new DsmInternalError("Unexpected data map size: " + pd.getDataMap().size());
            }
        });
        return candidateRecords.keySet();
    }

    private void updateParticipantDataList(Map<Integer, ParticipantData> participantData,
                                           Set<Integer> recordsToDelete) {
        if (recordsToDelete.isEmpty()) {
            return;
        }
        recordsToDelete.forEach(participantData::remove);
    }

    private void deleteParticipantData(Set<Integer> participantDataIds) {
        if (participantDataIds.isEmpty()) {
            return;
        }
        ParticipantDataDao dataDao = new ParticipantDataDao();
        participantDataIds.forEach(dataDao::delete);
    }
}
