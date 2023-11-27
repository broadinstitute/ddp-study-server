package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.INSTANCE_GUID;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetTempMailingAddressRoute implements Route {

    @Override
    public Object handle(Request request, Response response) {
        String activityInstanceGuid = request.params(INSTANCE_GUID);

        Optional<MailAddress> tempAddressOptional = TransactionWrapper.withTxn(handle -> {
            JdbiTempMailAddress tempAddressDao = handle.attach(JdbiTempMailAddress.class);
            return tempAddressDao.findTempAddressByActvityInstanceGuid(activityInstanceGuid);
        });
        return tempAddressOptional.orElseGet(() -> {
            ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND,
                    "Could not find temporary address for instance guid: " + activityInstanceGuid));
            return null;
        });
    }
}
