package org.broadinstitute.ddp.route;

import java.time.Instant;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.invitation.InvitationVerifyPayload;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * "verify" here means an end-user is verifying that they received communication from us via the contact email on the
 * invitation, mostly used in age-up scenarios.
 *
 * <p>NOTE: this is a public route. Be careful what we return in responses.
 */
public class InvitationVerifyRoute extends ValidatedJsonInputRoute<InvitationVerifyPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(InvitationVerifyRoute.class);

    @Override
    public Object handle(Request request, Response response, InvitationVerifyPayload invitation) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = invitation.getInvitationGuid();
        Instant verifiedAt = Instant.now();

        LOG.info("Attempting to verify invitation {} in study {}", invitationGuid, studyGuid);

        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                LOG.warn("Could not find study with guid {}", studyGuid);
                return;
            }

            InvitationDao invitationDao = handle.attach(InvitationDao.class);
            Optional<InvitationDto> invitationDto = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid);

            invitationDto.ifPresent(invite -> {
                if (invite.canBeVerified()) {
                    invitationDao.markVerified(invite.getInvitationId(), verifiedAt);
                    LOG.info("Invitation {} is verified at {}", invitationGuid, verifiedAt);
                } else {
                    LOG.warn("Invitation {} has already been verified or voided", invitationGuid);
                }
            });
        });

        // whether or not the invitation exists, and regardless of whether it has already
        // been verified, return ok so that we don't leak information on an open route
        response.status(HttpStatus.SC_OK);
        return null;
    }
}
