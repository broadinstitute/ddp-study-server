package org.broadinstitute.dsm.service.medicalrecord;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.service.participant.ParticipantService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPMedicalRecordDataRequest;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;

@Slf4j
public class MedicalRecordService {
    private final DDPInstanceProvider ddpInstanceProvider;

    public MedicalRecordService() {
        this(new MedicalRecordInstanceProvider());
    }

    public MedicalRecordService(DDPInstanceProvider ddpInstanceProvider) {
        this.ddpInstanceProvider = ddpInstanceProvider;
    }

    /**
     * Request new participant institutions from DDP for all instances that have medical records. Create a medical
     * record bundle for new each institution.
     */
    public void requestParticipantInstitutions() {
        List<DDPInstance> ddpInstances = ddpInstanceProvider.getApplicableInstances();
        if (ddpInstances.isEmpty()) {
            return;
        }
        // if there are errors with a particular instance, continue processing other instances since this will
        // retry at the correct sequence number in the next run
        for (DDPInstance ddpInstance : ddpInstances) {
            Optional<Long> seqNum = ddpInstanceProvider.getInstanceSequenceNumber(ddpInstance);
            if (seqNum.isEmpty()) {
                // note error and continue processing other instances
                log.error("No sequence number found for DDP instance {}", ddpInstance.getName());
                continue;
            }
            // request new participant institutions from DSS, starting at the last sequence number processed
            long seqStart = seqNum.get();
            String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_INSTITUTIONS + "/" + seqStart;

            InstitutionRequest[] institutionRequests = null;
            try {
                institutionRequests =
                        DDPRequestUtil.getResponseObject(InstitutionRequest[].class, dsmRequest, ddpInstance.getName(),
                                ddpInstance.isHasAuth0Token());
            } catch (Exception e) {
                // note error and continue processing other instances
                log.error("Error getting DDP participant institutions for " + ddpInstance.getName(), e);
            }
            if (institutionRequests != null && institutionRequests.length > 0) {
                processInstitutionRequest(institutionRequests, ddpInstance, seqStart);
            }
        }
    }

    protected boolean processInstitutionRequest(InstitutionRequest[] institutionRequests, DDPInstance ddpInstance,
                                                long seqStart) {
        log.info("Got {} InstitutionRequests for {}", institutionRequests.length, ddpInstance.getName());

        // sort requests by sequence number/timestamp so we process them in the correct order, should
        // we get an error
        List<InstitutionRequest> reqs = Arrays.stream(institutionRequests)
                .sorted(Comparator.comparingLong(InstitutionRequest::getId))
                .toList();
        return inTransaction(conn -> {
            if (ddpInstanceProvider.getInstanceSequenceNumber(ddpInstance, conn) != seqStart) {
                // presumably another thread processed this instance, so note and abort
                // this is not an error per se, since the updated sequence number will be used for the next run
                log.error("Sequence number mismatch for DDP instance {}", ddpInstance.getName());
                return false;
            }

            for (InstitutionRequest req : reqs) {
                long seqNumber = req.getId();
                if (seqNumber < seqStart) {
                    log.error("Skipping InstitutionRequest with id {} for DDP instance {}: Invalid sequence number",
                            req.getId(), ddpInstance.getName());
                    continue;
                }
                writeInstitutionBundle(conn, req,
                        ddpInstanceProvider.getEffectiveInstance(ddpInstance, req.getParticipantId()));
                // Note: we could update the sequence number once at the end, but that makes the error handling
                // more complex, so we update it for each request. The updates should be fast.
                ddpInstanceProvider.updateInstanceSequenceNumber(ddpInstance, seqNumber, conn);
            }
            return true;
        });
    }

    /**
     * Writes new institutions to DB, creates new medical records for the institutions, creates new onc history records,
     * creates new participant records, and updates the medical record log. If the participant does not already
     * exist, a new participant is created.
     *
     * @return created medical record IDs
     */
    public static List<Integer> writeInstitutionBundle(Connection conn, InstitutionRequest institutionRequest,
                                                       DDPInstance ddpInstance) {
        int instanceId = ddpInstance.getDdpInstanceIdAsInt();
        String ddpParticipantId = institutionRequest.getParticipantId();

        ParticipantDao participantDao = new ParticipantDao();
        Optional<ParticipantDto> ptp = participantDao.getParticipantForInstance(ddpParticipantId, instanceId);

        List<Integer> medicalRecordIds;
        if (ptp.isPresent()) {
            ParticipantDto participant = ptp.get();
            ParticipantService.updateLastVersion(participant.getParticipantIdOrThrow(), institutionRequest.getId(),
                    institutionRequest.getLastUpdated(), conn);
            medicalRecordIds = DDPMedicalRecordDataRequest.writeInstitutionInfo(conn, institutionRequest, participant, ddpInstance);

            // participant lastVersion changed so update medical record log
            DDPMedicalRecordDataRequest.updateMedicalRecordLog(conn, ddpParticipantId, instanceId);
        } else {
            ParticipantDto participantDto = ParticipantService.createParticipant(ddpParticipantId,
                    institutionRequest.getId(), institutionRequest.getLastUpdated(), ddpInstance, conn);
            medicalRecordIds = DDPMedicalRecordDataRequest.writeInstitutionInfo(conn, institutionRequest, participantDto, ddpInstance);
        }
        return medicalRecordIds;
    }

    public static List<Integer> writeInstitutionBundle(InstitutionRequest institutionRequest, DDPInstance ddpInstance) {
        return inTransaction(conn -> writeInstitutionBundle(conn, institutionRequest, ddpInstance));
    }
}
