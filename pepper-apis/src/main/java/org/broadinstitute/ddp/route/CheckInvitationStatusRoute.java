package org.broadinstitute.ddp.route;

import java.util.List;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.invitation.CheckInvitationStatusPayload;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * "check" here means we're checking the status of an invitation, e.g. does it exists and is valid?
 *
 * <p>NOTE: this is a public route. Be careful what we return in responses.
 */
public class CheckInvitationStatusRoute extends ValidatedJsonInputRoute<CheckInvitationStatusPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CheckInvitationStatusRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CheckInvitationStatusPayload payload) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();
        LOG.info("Attempting to check invitation {} in study {}", invitationGuid, studyGuid);

        int status = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                LOG.error("Invitation check called for non-existent study with guid {}", studyGuid);
                return HttpStatus.SC_BAD_REQUEST;
            }

            List<String> permittedStudies = handle.attach(JdbiClientUmbrellaStudy.class)
                    .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(payload.getAuth0ClientId(), payload.getAuth0Domain());
            if (permittedStudies.contains(studyGuid)) {
                LOG.info("Invitation check by client clientId={}, tenant={}",
                        payload.getAuth0ClientId(), payload.getAuth0Domain());
            } else {
                LOG.error("Invitation check by client which does not have access to study: clientId={}, tenant={}",
                        payload.getAuth0ClientId(), payload.getAuth0Domain());
                return HttpStatus.SC_BAD_REQUEST;
            }

            // todo: check recaptcha

            InvitationDao invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).orElse(null);
            if (invitation == null) {
                // It might just be a typo, so do a warn instead of error log.
                LOG.warn("Invitation {} does not exist", invitationGuid);
                return HttpStatus.SC_BAD_REQUEST;
            } else if (invitation.isVoid()) {
                LOG.error("Invitation {} is voided", invitationGuid);
                return HttpStatus.SC_BAD_REQUEST;
            } else if (invitation.isAccepted()) {
                LOG.error("Invitation {} has already been accepted", invitationGuid);
                return HttpStatus.SC_BAD_REQUEST;
            } else {
                LOG.info("Invitation {} is valid", invitationGuid);
                return HttpStatus.SC_OK;
            }
        });

        response.status(status);
        return null;
    }
}
