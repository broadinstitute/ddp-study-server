package org.broadinstitute.ddp.filter;

import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.MDC;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Adds client and user data to logging infrastructure.
 */
public class AddDDPAuthLoggingFilter implements Filter {

    /**
     * Should match the %X{} block in logging config files.
     */
    public static final String LOGGING_CLIENTID_PARAM = "ClientId";

    /**
     * Should match the %X{} block in logging config files.
     */
    public static final String LOGGING_USERID_PARAM = "UserId";

    public static final String FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public void handle(Request request, Response response) throws Exception {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        MDC.put(LOGGING_CLIENTID_PARAM, ddpAuth.getClient());
        MDC.put(LOGGING_USERID_PARAM, ddpAuth.getOperator());
    }
}
