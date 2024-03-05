package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataService;
import org.broadinstitute.dsm.service.participantdata.DuplicateParticipantData;
import org.broadinstitute.dsm.service.participantdata.ParticipantDataService;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * AdminOperation service to asynchronously fix various ParticipantData issues.
 * </p>
 * The operation requires a fixupType attribute. The supported fixup types are:
 * - atcpGenomicId: remove duplicate genomic IDs and participant exit statuses
 * - atcpLegacyPid: for the ddp_participant_id field, replace legacy PIDs with GUIDs. Report errors
 *   if records with legacy PIDs have the same field type ID as records with GUIDs.
 * </p>
 * The operation requires a dryRun attribute. If true, no data is changed but the UpdateLog will report
 * what would have been done.
 * </p>
 * For the atcpGenomicId fixup, the operation has an optional force attribute. If true, the operation will
 * delete records with duplicate field type IDs even if they have multiple data map entries.
 * </p>
 * The operation payload is a ParticipantListRequest (required for atcpGenomicId). If not present, all participants
 * in the realm are considered.
 */
@Slf4j
public class ParticipantDataFixupService extends ParticipantAdminOperationService {
    protected List<String> validRealms = List.of("atcp");
    private boolean forceFlag = false;
    private DDPInstance ddpInstance;
    private Map<String, List<ParticipantData>> participantDataByPtpId;
    private FixupType fixupType;
    private boolean dryRun;

    protected enum FixupType {
        AT_GENOMIC_ID("atcpGenomicId"),
        AT_LEGACY_ID("atcpLegacyId");

        public final String label;

        private FixupType(String label) {
            this.label = label;
        }

        public static FixupType valueOfLabel(String label) {
            for (FixupType fixupType : values()) {
                if (fixupType.label.equals(label)) {
                    return fixupType;
                }
            }
            return null;
        }
    }

    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId     ID of user performing operation
     * @param realm      realm for fixup
     * @param attributes unused
     * @param payload    request body, if any, as ParticipantDataFixupRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        ddpInstance = getDDPInstance(realm, validRealms);

        String fixupTypeArg = attributes.get("fixupType");
        if (StringUtils.isBlank(fixupTypeArg)) {
            throw new DSMBadRequestException("Missing required attribute 'fixupType'");
        }

        fixupType = FixupType.valueOfLabel(fixupTypeArg);
        if (fixupType == null) {
            throw new DSMBadRequestException(
                    "Invalid fixupType: %s. Valid fixup types: %s".formatted(fixupType, FixupType.values()));
        }

        dryRun = isRequiredDryRun(attributes);
        if (fixupType == FixupType.AT_LEGACY_ID && !dryRun) {
            throw new DSMBadRequestException("dryRun argument required for fixupType: %s".formatted(fixupType));
        }

        if (attributes.containsKey("force")) {
            String force = attributes.get("force");
            if (!StringUtils.isBlank(force)) {
                throw new DSMBadRequestException("Invalid 'force' attribute: not expecting a value");
            }
            forceFlag = true;
        }

        // handle optional list of ptps
        if (!StringUtils.isBlank(payload)) {
            participantDataByPtpId = getParticipantData(payload);
        } else {
            if (fixupType != FixupType.AT_LEGACY_ID) {
                //TODO: it feels too easy to inadvertently forget to include the payload, so for now we will require it.
                throw new DSMBadRequestException("Missing required payload");
            }
            // get study participants and their data
            // Implementation note: we can either gather all the ptp data now (fewer queries, larger memory footprint),
            // or just gather the ptp IDs here and query for ptp data separately (more queries, smaller memory footprint)
            // At the time of implementation ptps per study were reasonably small (~100) and ptp data size was not large
            List<ParticipantData> dataList =
                    participantDataDao.getParticipantDataByInstanceId(ddpInstance.getDdpInstanceIdAsInt());
            participantDataByPtpId = dataList.stream().collect(Collectors.groupingBy(ParticipantData::getRequiredDdpParticipantId));
        }
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLog;

        if (fixupType == FixupType.AT_GENOMIC_ID) {
            updateLog = new ArrayList<>();
            // for each participant attempt an update and log results
            for (var entry: participantDataByPtpId.entrySet()) {
                updateLog.add(updateGenomicId(entry.getKey(), entry.getValue()));
            }
        } else if (fixupType == FixupType.AT_LEGACY_ID) {
            updateLog = fixupLegacyPidData(participantDataByPtpId, ddpInstance.getParticipantIndexES());
        } else {
            throw new DsmInternalError("Invalid fixupType: " + fixupType);
        }

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected UpdateLog updateGenomicId(String ddpParticipantId, List<ParticipantData> participantDataList) {
        if (participantDataList.isEmpty()) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA.name());
        }
        // no duplicates
        if (participantDataList.size() == 1) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED.name());
        }

        try {
            Set<Integer> genomeIdToDelete =
                    getRecordsToDelete(participantDataList, ATParticipantDataService.AT_GROUP_GENOME_STUDY);
            Set<Integer> exitToDelete =
                    getRecordsToDelete(participantDataList, ATParticipantDataService.AT_PARTICIPANT_EXIT);
            log.info("Found {} genomic IDs and {} exit statuses to delete for participant {}",
                    genomeIdToDelete.size(), exitToDelete.size(), ddpParticipantId);

            if (genomeIdToDelete.isEmpty() && exitToDelete.isEmpty()) {
                return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED.name());
            }

            // remove records from DB
            deleteParticipantData(genomeIdToDelete);
            deleteParticipantData(exitToDelete);

            // update ES
            Map<Integer, ParticipantData> idToData = participantDataList.stream()
                    .collect(Collectors.toMap(ParticipantData::getParticipantDataId, pd -> pd));
            updateParticipantDataList(idToData, genomeIdToDelete);
            updateParticipantDataList(idToData, exitToDelete);
            ElasticSearchService.updateEsParticipantData(ddpParticipantId,
                    new ArrayList<>(idToData.values()), ddpInstance);
        } catch (Exception e) {
            String msg = String.format("Exception in ParticipantDataFixupService.run for participant %s: %s",
                    ddpParticipantId, e);
            // many of these exceptions will require investigation, but conservatively we will just log
            // at error level for those that are definitely concerning
            if (e instanceof DsmInternalError) {
                log.error(msg);
                e.printStackTrace();
            } else {
                log.warn(msg);
            }
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR.name(), e.toString());
        }
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.UPDATED.name());
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

        if (!forceFlag) {
            // we are expecting the records to delete to have only one data map entry
            candidateRecords.values().forEach(pd -> {
                if (pd.getDataMap().size() > 1) {
                    throw new DsmInternalError("Unexpected data map size: " + pd.getDataMap().size());
                }
            });
        }
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

    protected List<UpdateLog> fixupLegacyPidData(Map<String, List<ParticipantData>> participantDataMap,
                                                 String esIndex) {
        ElasticSearchService elasticSearchService = new ElasticSearchService();
        Map<String, String> ptpIdToLegacyPid = elasticSearchService.getLegacyPidsByGuid(esIndex);

        List<UpdateLog> updateLog = new ArrayList<>();
        for (var entry: participantDataMap.entrySet()) {
            updateLog.add(updateLegacyPid(entry.getKey(), entry.getValue(), ptpIdToLegacyPid));
        }
        return updateLog;
    }

    protected UpdateLog updateLegacyPid(String ddpParticipantId, List<ParticipantData> participantDataList,
                                        Map<String, String> ptpIdToLegacyPid) {
        UpdateLog updateLog = new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED.name());

        // we will find data attributed to legacy PIDs via the ptp GUID (see below)
        if (!ParticipantUtil.isGuid(ddpParticipantId)) {
            return updateLog;
        }
        if (participantDataList.isEmpty()) {
            updateLog.setStatus(UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA.name());
            return updateLog;
        }

        String legacyPid = ptpIdToLegacyPid.get(ddpParticipantId);
        if (StringUtils.isBlank(legacyPid)) {
            return updateLog;
        }

        // see if there are any records with legacy PID
        List<ParticipantData> ptpData = participantDataDao.getParticipantData(legacyPid);
        if (participantDataList.isEmpty()) {
            return updateLog;
        }

        // check for overlapping field type IDs
        Set<String> fieldTypeIds = participantDataList.stream()
                .map(ParticipantData::getRequiredFieldTypeId)
                .collect(Collectors.toSet());

        Set<String> legacyFieldTypeIds = ptpData.stream()
                .map(ParticipantData::getRequiredFieldTypeId)
                .collect(Collectors.toSet());

        fieldTypeIds.retainAll(legacyFieldTypeIds);
        if (fieldTypeIds.isEmpty()) {
            return updateLog;
        }

        log.info("Overlapping field type IDs between legacy and GUID records: {}", fieldTypeIds);

        List<DuplicateParticipantData> duplicateData = new ArrayList<>();
        fieldTypeIds.forEach(fieldTypeId -> {
            List<ParticipantData> duplicates = new ArrayList<>(participantDataList.stream()
                    .filter(pd -> pd.getRequiredFieldTypeId().equals(fieldTypeId)).toList());
            duplicates.addAll(ptpData.stream()
                    .filter(pd -> pd.getRequiredFieldTypeId().equals(fieldTypeId)).toList());

            duplicateData.addAll(ParticipantDataService.handleDuplicateRecords(duplicates));
        });
        updateLog.setDuplicateParticipantData(duplicateData);
        return updateLog;
    }
}
