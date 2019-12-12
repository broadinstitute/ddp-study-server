package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetParticipantMailAddressRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetParticipantMailAddressRoute.class);

    private AddressService addressService;

    public GetParticipantMailAddressRoute(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String participantGuid = request.params(USER_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        LOG.info("Retrieving mail address for participant: {} and operator: {}", participantGuid, operatorGuid);
        return addressService.findAllAddressesForParticipant(participantGuid);
    }
}
