package org.broadinstitute.ddp.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.exception.AddressVerificationException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class VerifyMailAddressRoute extends ValidatedJsonInputRoute<MailAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(VerifyMailAddressRoute.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private AddressService addressService;


    public VerifyMailAddressRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Object handle(Request request, Response response, MailAddress dataObject) {
        LOG.info("Verifying mail address");
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Verifying address: {}", GSON.toJson(dataObject));
            }
            return addressService.verifyAddress(dataObject);
        } catch (AddressVerificationException e) {
            ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getError());
            return null;
        }

    }

}
