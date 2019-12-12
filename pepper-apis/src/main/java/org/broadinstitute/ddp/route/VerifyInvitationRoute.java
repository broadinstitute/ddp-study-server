package org.broadinstitute.ddp.route;

import java.sql.Timestamp;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.InvitationPayload;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.TimestampUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class VerifyInvitationRoute extends ValidatedJsonInputRoute<InvitationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyInvitationRoute.class);

    @Override
    public Object handle(Request request, Response response, InvitationPayload invitation) throws Exception {
        String invitationGuid = invitation.getInvitationId();
        Timestamp verifiedAt = TimestampUtil.now();

        TransactionWrapper.useTxn(handle -> {
            InvitationDao invitationDao = handle.attach(InvitationDao.class);
            Optional<InvitationDto> invitationDto = invitationDao.findByInvitationGuid(invitationGuid);

            invitationDto.ifPresent(invite -> {
                if (invite.canBeVerified()) {
                    int numRows = invitationDao.updateVerifiedAt(verifiedAt, invitationGuid);

                    if (numRows > 1) {
                        LOG.error("Updated too many rows ("  + numRows + ") for invitation " + invitationGuid);
                        ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, "");
                    }
                }
            });

            // whether or not the invitation exists, and regardless of whether it has already
            // been verified, return ok so that we don't leak information on an open route
            response.status(HttpStatus.SC_OK);
        });
        return "";
    }
}
