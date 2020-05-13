package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.constants.ErrorCodes.TOO_MANY_REQUESTS;

import java.util.Map;
import javax.servlet.ServletException;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.servlets.DoSFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

public class RateLimitFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequestsPerSecond;

    private final int burst;

    private final DoSFilter rateLimitFilter;

    public RateLimitFilter(int maxRequestsPerSecond, int burst) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.burst = burst;
        DoSFilter rateLimitFilter = new DoSFilter();
        rateLimitFilter.setEnabled(true);
        try {
            rateLimitFilter.init(new FilterConfig("DOSFilter", Map.of("delayMs", "-1",
                    "maxRequestsPerSec", Integer.toString(maxRequestsPerSecond),
                    "insertHeaders", "false",
                    "throttledRequests", Integer.toString(burst))));
        } catch (ServletException e) {
            throw new DDPException("Could not initialize rate limits", e);
        }
        this.rateLimitFilter = rateLimitFilter;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        rateLimitFilter.doFilter(request.raw(), response.raw(), (req, res) -> { });
        if (HttpStatus.TOO_MANY_REQUESTS_429 == response.raw().getStatus()) {
            response.status(HttpStatus.TOO_MANY_REQUESTS_429);
            LOG.info("REJECTED!");
            ResponseUtil.haltError(HttpStatus.TOO_MANY_REQUESTS_429, new ApiError(TOO_MANY_REQUESTS, TOO_MANY_REQUESTS));
        }
    }
}
