package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.INSTANCE_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
public class PutTempMailingAddressRoute extends ValidatedJsonInputRoute<MailAddress> {
    @Override
    public Object handle(Request request, Response response, MailAddress tempMailAddress) {
        String participantGuid = request.params(USER_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        String activityInstanceGuid = request.params(INSTANCE_GUID);
        log.info("Creating temp mailing address for participant {} by operator {} for activity instance {}",
                participantGuid, operatorGuid, activityInstanceGuid);
        if (log.isDebugEnabled()) {
            log.debug("About to save: {}", getGson().toJson(tempMailAddress));
        }

        boolean noActivityInstanceGuidPresent = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiActivityInstance.class).getByActivityInstanceGuid(activityInstanceGuid).isEmpty()
        );

        if (noActivityInstanceGuidPresent) {
            response.status(HttpStatus.SC_NOT_FOUND);
            return "";
        }

        boolean alreadyExists = TransactionWrapper.withTxn(handle -> {
            JdbiTempMailAddress jdbiTempMailAddress = handle.attach(JdbiTempMailAddress.class);
            boolean exists = (jdbiTempMailAddress.findTempAddressByActvityInstanceGuid(activityInstanceGuid).isPresent());
            jdbiTempMailAddress.saveTempAddress(tempMailAddress, participantGuid,
                    operatorGuid, activityInstanceGuid);

            return exists;
        });

        if (alreadyExists) {
            response.status(HttpStatus.SC_NO_CONTENT);
        } else {
            response.status(HttpStatus.SC_CREATED);
        }
        return "";
    }
}
