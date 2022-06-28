package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.ADDRESS_GUID;

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
public class GetMailAddressRoute implements Route {
    private AddressService addressService;

    @Override
    public Object handle(Request request, Response response) {
        String guid = request.params(ADDRESS_GUID);
        log.info("Requesting mail address with GUID: {}", guid);
        Optional<MailAddress> address = TransactionWrapper.withTxn(handle -> addressService.findAddressByGuid(handle, guid));
        if (address.isPresent()) {
            return address.get();
        } else {
            String errorMsg = "Mail address with guid '" + guid + "' was not found";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
        }
    }
}
