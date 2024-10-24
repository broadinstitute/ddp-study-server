package org.broadinstitute.dsm.service.adminoperation;

import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.FAMILY_ID;
import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.MEMBER_TYPE;
import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.MEMBER_TYPE_SELF;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.participantdata.DuplicateParticipantData;
import org.broadinstitute.dsm.service.participantdata.FamilyIdProvider;
import org.broadinstitute.dsm.service.participantdata.ParticipantDataService;
import org.broadinstitute.dsm.service.participantdata.RgpFamilyIdProvider;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * Service to initialize RGP participant data for participants that have not yet been initialized.
 * The service also detects errors in participant data, fixes those that have a direct fix,
 * and logs errors for those that require investigation. The 'logging' is part of the operation result which is
 * stored in the DB. See AdminOperationRecord for details.
 * </p>
 * This service has a dry-run mode that logs changes that would be made without actually making them,
 * including logging errors. Dry-run mode should always be run first to ensure the operation will yield the expected
 * results.
 */
@Slf4j
public class ParticipantInitService extends ParticipantAdminOperationService {

    protected List<String> validRealms = List.of("rgp");
    private DDPInstance ddpInstance;
    private Map<String, Map<String, Object>> esParticipants;
    private boolean isDryRun = true;

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

        // require dryRun since the user cannot specify a participant list
        isDryRun = isRequiredDryRun(attributes);

        esParticipants =
                ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES());
        log.info("Found {} participants in ES for instance {}", esParticipants.size(), ddpInstance.getName());
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLogs = new ArrayList<>();

        ElasticSearch elasticSearch = new ElasticSearch();
        // for each participant attempt an update and log results
        for (var entry: esParticipants.entrySet()) {
            updateLogs.add(initParticipant(entry.getKey(),
                    elasticSearch.parseSourceMap(entry.getValue(), entry.getKey()),
                    ddpInstance, new RgpFamilyIdProvider(), isDryRun));
        }

        // update job log record
        try {
            String json = ObjectMapperSingleton.writeValueAsString(updateLogs);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected static UpdateLog initParticipant(String ddpParticipantId, ElasticSearchParticipantDto esParticipant,
                                               DDPInstance ddpInstance, FamilyIdProvider familyIdProvider,
                                               boolean isDryRun) {
        // skip legacy participants since they are already initialized, and the code here, as is, does not handle them
        if (!ParticipantUtil.isGuid(ddpParticipantId)) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED,
                    "Legacy participant, skipping");
        }

        ParticipantData probandData = null;
        List<ParticipantData> ptpData = RgpParticipantDataService.getRgpParticipantData(ddpParticipantId);
        if (!ptpData.isEmpty()) {
            // preset to error, but it may not be
            UpdateLog updateLog = new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR);
            probandData = getProbandRgpData(ptpData, updateLog);
            if (probandData == null) {
                return updateLog;
            }
        }

        Optional<Dsm> dsm = esParticipant.getDsm();
        if (dsm.isPresent() && StringUtils.isNotBlank(dsm.get().getFamilyId())) {
            if (probandData == null) {
                return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR,
                        "Participant has family ID in ES but no RGP data");
            }
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED);
        }

        // participant has no family ID in ES
        try {
            // ptp does not have a family ID in ES, but does have RGP participant data
            if (probandData != null) {
                return updateESFamilyId(probandData, ddpParticipantId, ddpInstance.getParticipantIndexES(), isDryRun);
            }
            return createDefaultData(ddpParticipantId, esParticipant, ddpInstance, familyIdProvider, isDryRun);
        } catch (ESMissingParticipantDataException e) {
            // no profile
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA);
        } catch (Exception e) {
            String msg = String.format("Exception in ParticipantInitService.run for participant %s: %s",
                    ddpParticipantId, e);
            // many of these exceptions will require investigation, but conservatively we will just log
            // at error level for those that are definitely concerning
            if (e instanceof DsmInternalError) {
                log.error(msg);
                e.printStackTrace();
            } else {
                log.warn(msg);
            }
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR, e.toString());
        }
    }

    protected static ParticipantData getProbandRgpData(List<ParticipantData> ptpData, UpdateLog updateLog) {
        List<ParticipantData> probandData = ptpData.stream()
                .filter(data -> MEMBER_TYPE_SELF.equals(data.getDataMap().get(MEMBER_TYPE)))
                .toList();
        if (probandData.isEmpty()) {
            updateLog.setError("Participant has RGP data but no proband record");
            return null;
        }
        if (probandData.size() > 1) {
            log.info("Participant has {} RGP proband records", probandData.size());
            List<DuplicateParticipantData> duplicateData = ParticipantDataService.handleDuplicateRecords(probandData);
            updateLog.setDuplicateParticipantData(duplicateData);
            return null;
        }

        ParticipantData participantData = probandData.get(0);
        String familyId = participantData.getDataMap().get(FAMILY_ID);
        if (StringUtils.isBlank(familyId)) {
            updateLog.setError("Participant has proband RGP data that does not contain a family ID");
        }
        return participantData;
    }

    protected static UpdateLog createDefaultData(String ddpParticipantId, ElasticSearchParticipantDto esParticipant,
                                                 DDPInstance ddpInstance, FamilyIdProvider familyIdProvider,
                                                 boolean isDryRun) {
        if (isDryRun) {
            if (esParticipant.getProfile().isEmpty()) {
                throw new ESMissingParticipantDataException("Participant does not yet have profile in ES");
            }
        } else {
            RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipant, ddpInstance, familyIdProvider);
        }
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.UPDATED);
    }

    /**
     * Handle case where participant has RGP data but no family ID in ES.
     * @param probandData proband participant RGP data, already checked for errors
     */
    protected static UpdateLog updateESFamilyId(ParticipantData probandData, String ddpParticipantId, String esIndex,
                                                boolean isDryRun) {
        String familyId = probandData.getDataMap().get(FAMILY_ID);
        if (StringUtils.isBlank(familyId)) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR,
                    "Participant has proband RGP data that does not contain a family ID");
        }
        if (!isDryRun) {
            RgpParticipantDataService.insertEsFamilyId(esIndex, ddpParticipantId, Long.parseLong(familyId));
        }
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ES_UPDATED);
    }
}
