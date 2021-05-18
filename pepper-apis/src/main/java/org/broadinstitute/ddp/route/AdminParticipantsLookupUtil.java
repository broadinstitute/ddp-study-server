package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_REQUEST;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.broadinstitute.ddp.util.RouteUtil;

public class AdminParticipantsLookupUtil {

    public static void handleParticipantLookupException(ParticipantsLookupException e, RouteUtil.HaltErrorExecutor haltErrorExecutor) {
        String code;
        int status;
        switch (e.getErrorType()) {
            case INVALID_RESULT_MAX_COUNT:
                status = SC_BAD_REQUEST;
                code = INVALID_REQUEST;
                break;
            case SEARCH_ERROR:
                status = SC_INTERNAL_SERVER_ERROR;
                code = e.getErrorCode();
                break;
            default:
                throw new DDPException("Unknown participants lookup error type");
        }
        haltErrorExecutor.haltError(status, code, e.getExtendedMessage());
    }
}
