package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class GetParticipantDefaultMailAddressRoute implements Route {
    private final AddressService addressService;

    @Override
    public Object handle(Request request, Response response) {
        String participantGuid = request.params(USER_GUID);
        log.info("Retrieving default mail address for participant: {})", participantGuid);
        Optional<MailAddress> optionalAddress = TransactionWrapper.withTxn(handle -> addressService
                .findDefaultAddressForParticipant(handle, participantGuid));
        if (optionalAddress.isPresent()) {
            return optionalAddress.get();
        } else {
            String errorMsg = "Default mail address for participant '" + participantGuid + "' was not found";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
        }
    }
}
