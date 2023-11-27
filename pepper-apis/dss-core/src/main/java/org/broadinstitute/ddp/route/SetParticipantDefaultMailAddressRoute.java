package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.SetDefaultMailAddressPayload;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class SetParticipantDefaultMailAddressRoute extends ValidatedJsonInputRoute<SetDefaultMailAddressPayload> {
    private final AddressService addressService;

    @Override
    public Object handle(Request request, Response response, SetDefaultMailAddressPayload defaultAddressObj) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String addressGuid = defaultAddressObj.getAddressGuid();
        log.info("Setting address with GUID: {} to be the default", addressGuid);
        return TransactionWrapper.withTxn(handle -> {
            boolean wasSet = addressService.setAddressAsDefault(handle, addressGuid);
            if (wasSet) {
                handle.attach(DataExportDao.class).queueDataSync(userGuid);
                response.status(HttpStatus.SC_NO_CONTENT);
                return "";
            } else {
                String errorMsg = "Mail address with guid '" + addressGuid + "' was not found";
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
            }
        });
    }
}
