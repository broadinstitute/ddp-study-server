package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiCountryAddressInfo;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetCountryAddressInfoSummariesRoute implements Route {

    @Override
    public Object handle(Request request, Response response) {
        return TransactionWrapper.withTxn((handle) -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            return dao.getAllOrderedSummaries();
        });
    }
}
