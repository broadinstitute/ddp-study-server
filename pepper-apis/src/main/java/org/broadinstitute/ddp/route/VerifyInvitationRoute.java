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
import org.broadinstitute.ddp.json.invitation.VerifyInvitationPayload;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class VerifyInvitationRoute extends ValidatedJsonInputRoute<VerifyInvitationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyInvitationRoute.class);

    @Override
    public Object handle(Request request, Response response, VerifyInvitationPayload invitation) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = invitation.getInvitationId();
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
                    int numRows = invitationDao.updateVerifiedAt(invite.getInvitationId(), verifiedAt);
                    if (numRows > 1) {
                        LOG.error("Updated too many rows ("  + numRows + ") for invitation " + invitationGuid);
                        throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, "");
                    }
                }
            });
        });

        // whether or not the invitation exists, and regardless of whether it has already
        // been verified, return ok so that we don't leak information on an open route
        response.status(HttpStatus.SC_OK);
        return "";
    }
}
