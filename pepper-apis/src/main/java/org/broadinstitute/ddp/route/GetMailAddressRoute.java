package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.ADDRESS_GUID;

import java.util.Optional;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetMailAddressRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetMailAddressRoute.class);

    private AddressService addressService;

    public GetMailAddressRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String guid = request.params(ADDRESS_GUID);
        LOG.info("Requesting mail address with GUID: {}", guid);
        Optional<MailAddress> address = addressService.findAddressByGuid(guid);
        if (address.isPresent()) {
            return address.get();
        } else {
            String errorMsg = "Mail address with guid '" + guid + "' was not found";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
        }
    }
}
