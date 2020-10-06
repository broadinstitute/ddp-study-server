package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JbdiActivityInstanceStatusTypeCached;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatusType;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetActivityInstanceStatusTypeListRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetActivityInstanceStatusTypeListRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        return TransactionWrapper.withTxn(
                handle -> {
                    DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
                    return handle.attach(JbdiActivityInstanceStatusTypeCached.class)
                            .getActivityInstanceStatusTypes(ddpAuth.getPreferredLanguage());
                }
        );
    }
}
