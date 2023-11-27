package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.COUNTRY_CODE;

import java.util.Optional;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiCountryAddressInfo;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetCountryAddressInfoRoute implements Route {



    @Override
    public Object handle(Request request, Response response) {
        String countryCode = request.params(COUNTRY_CODE);
        return TransactionWrapper.withTxn((handle) -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            Optional<CountryAddressInfo> optionalCountryAddress = dao.getCountryAddressInfo(countryCode);
            if (optionalCountryAddress.isPresent()) {
                return optionalCountryAddress.get();
            } else {
                ApiError apiError = new ApiError(ErrorCodes.NOT_FOUND, "Country was not found");
                response.type(ContentType.APPLICATION_JSON.getMimeType());
                return apiError;
            }
        });

    }
}
