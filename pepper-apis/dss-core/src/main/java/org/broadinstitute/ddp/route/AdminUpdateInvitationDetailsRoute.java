package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.admin.UpdateInvitationDetailsPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class AdminUpdateInvitationDetailsRoute extends ValidatedJsonInputRoute<UpdateInvitationDetailsPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(AdminUpdateInvitationDetailsRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, UpdateInvitationDetailsPayload payload) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();
        LOG.info("Attempting to update invitation {} in study {}", invitationGuid, studyGuid);

        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            var invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).orElse(null);
            if (invitation == null) {
                String msg = "Could not find invitation " + invitationGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            invitationDao.saveNotes(invitation.getInvitationId(), payload.getNotes());
            LOG.info("Saved notes for invitation {}", invitationGuid);
        });

        response.status(HttpStatus.SC_OK);
        return null;
    }
}
