package org.broadinstitute.ddp.route;

import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
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
import spark.Request;
import spark.Response;

@Slf4j
public class AdminLookupInvitationRoute extends ValidatedJsonInputRoute<LookupInvitationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, LookupInvitationPayload payload) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String invitationGuid = payload.getInvitationGuid();
        log.info("Attempting to get invitation {} in study {}", invitationGuid, studyGuid);

        var result = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                log.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            var invitationDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).orElse(null);
            if (invitation == null) {
                String msg = "Could not find invitation " + invitationGuid;
                log.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            if (invitation.getUserId() == null) {
                return new LookupInvitationResponse(invitation, null, null);
            } else {
                User user = handle.attach(UserDao.class).findUserById(invitation.getUserId()).orElse(null);
                if (user == null) {
                    log.error("Could not find user for invitation {}", invitationGuid);
                    throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.SERVER_ERROR, ""));
                }

                String loginEmail = null;
                
                if (user.hasAuth0Account()) {
                    var auth0UserId = user.getAuth0UserId().get();

                    Auth0ManagementClient mgmtClient = Auth0ManagementClient.forStudy(handle, studyGuid);
                    Map<String, String> emailResults = new Auth0Util(mgmtClient.getDomain())
                            .getEmailsByAuth0UserIdsAndConnection(Set.of(auth0UserId),
                                mgmtClient.getToken(),
                                studyDto.getDefaultAuth0Connection());
                    loginEmail = emailResults.get(auth0UserId);
                }

                return new LookupInvitationResponse(invitation, user.getGuid(), loginEmail);
            }
        });

        response.status(HttpStatus.SC_OK);
        return result;
    }
}
