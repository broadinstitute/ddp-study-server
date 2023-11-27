package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.Invitation;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class ListUserStudyInvitationsRoute implements Route {
    @Override
    public Object handle(Request request, Response response) throws Exception {
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        log.info("Attempting to lookup invitations for participant {} in study {} by operator {}",
                participantGuid, studyGuid, operatorGuid);

        List<InvitationDto> inviteDtos = TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
            long studyId = found.getStudyDto().getId();
            long userId = found.getUser().getId();
            return handle.attach(InvitationDao.class).findInvitations(studyId, userId);
        });

        // Do JSON transformation outside of transaction so we release the handle as soon as possible.
        List<Invitation> invites = inviteDtos.stream()
                .map(Invitation::new)
                .collect(Collectors.toList());

        log.info("Participant {} in study {} has {} invitations", participantGuid, studyGuid, invites.size());
        return invites;
    }
}
