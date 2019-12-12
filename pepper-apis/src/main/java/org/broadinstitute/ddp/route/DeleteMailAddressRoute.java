package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.ADDRESS_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class DeleteMailAddressRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteMailAddressRoute.class);

    private AddressService addressService;

    public DeleteMailAddressRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(USER_GUID);
        String guid = request.params(ADDRESS_GUID);
        LOG.info("Will delete address with GUID: {}", guid);
        return TransactionWrapper.withTxn(handle -> {
            boolean wasDeleted = addressService.deleteAddress(handle, guid);
            if (wasDeleted) {
                handle.attach(DataExportDao.class).queueDataSync(userGuid);
                LOG.info("Address with GUID: {} deleted", guid);
                response.status(HttpStatus.SC_NO_CONTENT);
                return "";
            } else {
                String errorMsg = "Mail address with guid '" + guid + "' was not found";
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
            }
        });
    }
}
