package org.broadinstitute.dsm.service.medicalrecord;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.service.participant.OsteoParticipantService;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.DDPMedicalRecordDataRequest;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;

@Slf4j
public class MedicalRecordService {
    private static final BookmarkDao bookmarkDao = new BookmarkDao();
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();

    /**
     * Request new participant institutions from DDP, and create a medical record bundle for each institution
     */
    public static void requestParticipantInstitutions() {
        List<DDPInstance> ddpInstances =
                DDPInstance.getDDPInstanceListWithRole(DBConstants.HAS_MEDICAL_RECORD_ENDPOINTS);
        if (ddpInstances.isEmpty()) {
            return;
        }
        for (DDPInstance ddpInstance : ddpInstances) {
            // bookmark keeps the last sequence number processed
            Optional<BookmarkDto> bookMark = bookmarkDao.getBookmarkByInstance(ddpInstance.getDdpInstanceId());
            if (bookMark.isEmpty()) {
                // process other instances
                log.error("No bookmark found for DDP instance {}", ddpInstance.getName());
                continue;
            }
            // ask DDP for participant institutions from the last sequence number processed
            long seqStart = bookMark.get().getValue();
            String dsmRequest = ddpInstance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_INSTITUTIONS + "/" + seqStart;

            InstitutionRequest[] institutionRequests = null;
            try {
                institutionRequests =
                        DDPRequestUtil.getResponseObject(InstitutionRequest[].class, dsmRequest, ddpInstance.getName(),
                                ddpInstance.isHasAuth0Token());
            } catch (Exception e) {
                // process other instances
                log.error("Error getting DDP participant institutions for " + ddpInstance.getName(), e);
            }
            if (institutionRequests != null && institutionRequests.length > 0) {
                processInstitutionRequest(institutionRequests, ddpInstance, seqStart);
            }
        }
    }

    protected static boolean processInstitutionRequest(InstitutionRequest[] institutionRequests, DDPInstance ddpInstance,
                                                       long seqStart) {
        log.info("Got {} InstitutionRequests for {}", institutionRequests.length, ddpInstance.getName());

        // sort requests by sequence number/timestamp so we process them in the correct order, should
        // we get an error
        List<InstitutionRequest> reqs = Arrays.stream(institutionRequests)
                .sorted(Comparator.comparingLong(InstitutionRequest::getId))
                .toList();
        return inTransaction(conn -> {
            if (DBUtil.getBookmark(conn, ddpInstance.getDdpInstanceId()) != seqStart) {
                // presumably another thread processed this instance, so note and abort
                // this is not an error per se, since the updated sequence number will be used for the next run
                log.error("Sequence number mismatch for DDP instance {}", ddpInstance.getName());
                return false;
            }

            long seqNumber = seqStart;
            for (InstitutionRequest req : reqs) {
                if (req.getId() < seqStart) {
                    log.error("Skipping InstitutionRequest with id {} for DDP instance {}: Invalid sequence number",
                            req.getId(), ddpInstance.getName());
                    continue;
                }
                try {
                    DDPMedicalRecordDataRequest.writeInstitutionBundle(conn, req,
                            getEffectiveInstance(ddpInstance, req.getParticipantId()));
                } catch (Exception e) {
                    // update bookmark with last successfully processed sequence number
                    DBUtil.updateBookmark(conn, seqNumber, ddpInstance.getDdpInstanceId());
                    throw e;
                }
                seqNumber = req.getId();
            }
            DBUtil.updateBookmark(conn, seqNumber, ddpInstance.getDdpInstanceId());
            return true;
        });
    }

    private static DDPInstanceDto getEffectiveInstance(DDPInstance ddpInstance, String ddpParticipantId) {
        // no special handling for throw since this is an invariant
        DDPInstanceDto ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(ddpInstance.getName())
                .orElseThrow();

        OsteoParticipantService osteoParticipantService = new OsteoParticipantService();
        if (osteoParticipantService.isOsteoInstance(ddpInstanceDto)
                && osteoParticipantService.isOnlyOsteo1Participant(ddpParticipantId)) {
            return osteoParticipantService.getOsteo1Instance();
        }
        return ddpInstanceDto;
    }
}
