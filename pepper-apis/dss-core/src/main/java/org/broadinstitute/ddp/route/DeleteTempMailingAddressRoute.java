package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.INSTANCE_GUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class DeleteTempMailingAddressRoute implements Route {
    @Override
    public Object handle(Request request, Response response) {
        String activityInstanceGuid = request.params(INSTANCE_GUID);
        log.info("About to delete temp address for activity instance: {}", activityInstanceGuid);
        int rowsDeleted = TransactionWrapper.withTxn(handle -> {
            JdbiTempMailAddress tempAddressDao = handle.attach(JdbiTempMailAddress.class);
            return tempAddressDao.deleteTempAddressByActivityInstanceGuid(activityInstanceGuid);
        });
        if (rowsDeleted > 0) {
            log.info("Temp address for activity instance: {} deleted", activityInstanceGuid);
            response.status(HttpStatus.SC_NO_CONTENT);
        } else {
            log.info("Temp address for activity instance: {} was not found. No deletion", activityInstanceGuid);
            response.status(HttpStatus.SC_NOT_FOUND);
        }
        return "";
    }
}
