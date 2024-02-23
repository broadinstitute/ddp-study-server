package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.admin.AdminOperation;
import org.broadinstitute.dsm.service.admin.AdminOperationRecord;
import org.broadinstitute.dsm.service.participantdata.RgpFamilyIdProvider;
import org.broadinstitute.dsm.service.participantdata.RgpParticipantDataService;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
public class ParticipantInitService implements AdminOperation {

    private static final Gson gson = new Gson();
    protected List<String> validRealms = List.of("rgp");
    private DDPInstance ddpInstance;
    private Map<String, Map<String, Object>> esParticipants;

    /**
     * Validate input and retrieve participant data, during synchronous part of operation handling
     *
     * @param userId     ID of user performing operation
     * @param realm      realm for fixup
     * @param attributes unused
     * @param payload    request body, if any, as ParticipantDataFixupRequest
     */
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        if (!validRealms.contains(realm.toLowerCase())) {
            throw new DsmInternalError("Invalid realm for ParticipantInitService: " + realm);
        }

        ddpInstance = DDPInstance.getDDPInstance(realm);
        if (ddpInstance == null) {
            throw new DsmInternalError("Invalid realm: " + realm);
        }

        String esIndex = ddpInstance.getParticipantIndexES();
        if (StringUtils.isEmpty(esIndex)) {
            throw new DsmInternalError("No ES participant index for study " + realm);
        }

        esParticipants =
                ElasticSearchUtil.getDDPParticipantsFromES(ddpInstance.getName(), esIndex);
        log.info("Found {} participants in ES for instance {}", esParticipants.size(), ddpInstance.getName());
    }

    /**
     * Run the asynchronous part of the operation, updating the AdminRecord with the operation results
     *
     * @param operationId ID for reporting results
     */
    public void run(int operationId) {
        List<UpdateLog> updateLog = new ArrayList<>();

        ElasticSearch elasticSearch = new ElasticSearch();
        // for each participant attempt an update and log results
        for (var entry: esParticipants.entrySet()) {
            updateLog.add(initParticipant(entry.getKey(),
                    elasticSearch.parseSourceMap(entry.getValue(), entry.getKey()), ddpInstance));
        }

        // update job log record
        try {
            String json = gson.toJson(updateLog);
            AdminOperationRecord.updateOperationRecord(operationId, AdminOperationRecord.OperationStatus.COMPLETED, json);
        } catch (Exception e) {
            log.error("Error writing operation log: {}", e.toString());
        }
    }

    protected static UpdateLog initParticipant(String ddpParticipantId, ElasticSearchParticipantDto esParticipant,
                                               DDPInstance ddpInstance) {
        Optional<Dsm> dsm = esParticipant.getDsm();
        if (dsm.isEmpty()) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NO_PARTICIPANT_DATA.name());
        }
        if (StringUtils.isNotBlank(dsm.get().getFamilyId())) {
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.NOT_UPDATED.name());
        }

        try {
            RgpParticipantDataService.createDefaultData(ddpParticipantId, esParticipant, ddpInstance,
                    new RgpFamilyIdProvider());
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
            return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.ERROR.name(), e.toString());
        }
        return new UpdateLog(ddpParticipantId, UpdateLog.UpdateStatus.UPDATED.name());
    }
}
