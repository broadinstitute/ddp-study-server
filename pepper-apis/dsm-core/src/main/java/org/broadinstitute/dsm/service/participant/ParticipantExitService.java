package org.broadinstitute.dsm.service.participant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.http.HttpStatusCodes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.EntityNotFound;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;


@Slf4j
public class ParticipantExitService {

    /**
     * Return a map of DDP participant ID to ParticipantExit object for all exited participants in the given realm
     */
    public Map<String, ParticipantExit> getExitedParticipants(String realm) {
        return ParticipantExit.getExitedParticipants(realm, true);
    }

    /**
     * Exit a participant from DSM and DSS
     * @param inDDP true if the participant is in DSS. (See note in _participantExit Java doc.)
     * @return a list of KitDiscard for the participant, which allows the user to select what should be done with dangling kits
     *      if they are returned for GP processing
     */
    public List<KitDiscard> exitParticipant(String realm, String ddpParticipantId, String userId, boolean inDDP) {
        _exitParticipant(realm, ddpParticipantId, userId, inDDP);

        // NOTE: The following code was lifted as-is from ParticipantExitRoute.java. I did not attempt to
        // clean it up since we do not have functional tests in place. We will verify with our system tests. -DC
        List<KitRequestShipping> kitRequests =
                KitRequestShipping.getKitRequestsByParticipant(realm, ddpParticipantId, true);
        List<KitDiscard> kitsNeedAction = new ArrayList<>();
        log.info("Found {} kit requests", kitRequests.size());
        Optional<DDPInstanceDto> maybeInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(realm);
        for (KitRequestShipping kit : kitRequests) {
            if (kit.getScanDate() != 0 && kit.getReceiveDate() == 0) {
                String discardId = KitDiscard.addKitToDiscard(kit.getDsmKitRequestId(), KitDiscard.HOLD);
                kitsNeedAction.add(new KitDiscard(discardId, kit.getKitTypeName(), KitDiscard.HOLD));
            } else {
                //refund label of kits which are not sent yet
                KitRequestShipping.refundKit(kit.getDsmKitRequestId(), DSMServer.getDDPEasypostApiKey(realm),
                        maybeInstance.orElse(null));
            }
        }
        return kitsNeedAction;
    }

    /**
     * Exit a participant from DSM and DSS
     * @param inDDP true if the participant is in DSS. Note: The role of this parameter is unclear. It is passed from
     *             the front end (and is always true) The pre-existing code (modified here) recorded it in the DB, but used
     *             different criteria to determine if the participant is in DSS, so we kept that intact. -DC
     */
    protected void _exitParticipant(String realm, String ddpParticipantId, String userId, boolean inDDP) {
        // assert input
        if (StringUtils.isBlank(realm) || StringUtils.isBlank(ddpParticipantId) || StringUtils.isBlank(userId)) {
            throw new DsmInternalError("Invalid parameters for exitParticipant");
        }

        long currentTime = System.currentTimeMillis();
        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.HAS_EXIT_PARTICIPANT_ENDPOINT);
        if (!instance.isHasRole()) {
            // role indicates whether DSM should call DSS to exit participant
            // NOTE: The role of 'inDDP' is unclear here, see comment above, so I left that parameter as is it was -DC
            ParticipantExit.exitParticipant(ddpParticipantId, currentTime, userId, instance, false);
            return;
        }

        // request DSS to exit participant
        String requestUrl = instance.getBaseUrl() + RoutePath.DDP_PARTICIPANT_EXIT_PATH + "/" + ddpParticipantId;
        Integer response;
        try {
            response = DDPRequestUtil.postRequest(requestUrl, null, instance.getName(), instance.isHasAuth0Token());
        } catch (Exception e) {
            throw new DsmInternalError("Error requesting DSS exit participant for URL " + requestUrl, e);
        }
        if (response == HttpStatusCodes.STATUS_CODE_OK) {
            log.info("Requesting DSS exit participant {}", ddpParticipantId);
            ParticipantExit.exitParticipant(ddpParticipantId, currentTime, userId, instance, inDDP);
        } else if (response == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
            throw new EntityNotFound("Participant not found in DSS. ID: %s".formatted(ddpParticipantId));
        } else {
            throw new DsmInternalError("Unexpected response code from DSS exit participant request: %d".formatted(response));
        }
    }
}
