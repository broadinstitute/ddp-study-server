package org.broadinstitute.ddp.route;

import java.util.Map;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.admin.LookupInvitationPayload;
import org.broadinstitute.ddp.json.admin.LookupInvitationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class AdminLookupInvitationRoute extends ValidatedJsonInputRoute<LookupInvitationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(AdminLookupInvitationRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, LookupInvitationPayload payload) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();
        LOG.info("Attempting to get invitation {} in study {}", invitationGuid, studyGuid);

        var result = TransactionWrapper.withTxn(handle -> {
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

            if (invitation.getUserId() == null) {
                return new LookupInvitationResponse(invitation, null, null);
            } else {
                User user = handle.attach(UserDao.class).findUserById(invitation.getUserId()).orElse(null);
                if (user == null) {
                    LOG.error("Could not find user for invitation {}", invitationGuid);
                    throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.SERVER_ERROR, ""));
                }

                String loginEmail = null;
                if (user.getAuth0UserId() != null) {
                    Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForStudy(handle, studyGuid);
                    Map<String, String> emailResults = new Auth0Util(mgmtClient.getDomain())
                            .getUserPassConnEmailsByAuth0UserIds(Set.of(user.getAuth0UserId()), mgmtClient.getToken());
                    loginEmail = emailResults.get(user.getAuth0UserId());
                }

                return new LookupInvitationResponse(invitation, user.getGuid(), loginEmail);
            }
        });

        response.status(HttpStatus.SC_OK);
        return result;
    }
}
