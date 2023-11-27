package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class GetParticipantMailAddressRoute implements Route {
    private final AddressService addressService;
    
    @Override
    public Object handle(Request request, Response response) {
        String participantGuid = request.params(USER_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        log.info("Retrieving mail address for participant: {} and operator: {}", participantGuid, operatorGuid);
        return TransactionWrapper.withTxn(handle -> addressService.findAllAddressesForParticipant(handle, participantGuid));
    }
}
