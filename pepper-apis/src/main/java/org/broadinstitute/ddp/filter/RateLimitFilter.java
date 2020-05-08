package org.broadinstitute.ddp.filter;

import java.util.Map;
import javax.servlet.ServletException;

import org.broadinstitute.ddp.exception.DDPException;
import org.eclipse.jetty.servlets.DoSFilter;
import spark.Filter;
import spark.Request;
import spark.Response;

public class RateLimitFilter implements Filter {

    private final int maxRequestsPerSecond;

    private final int burst;

    private final DoSFilter rateLimitFilter;

    public RateLimitFilter(int maxRequestsPerSecond, int burst) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.burst = burst;
        DoSFilter rateLimitFilter = new DoSFilter();
        rateLimitFilter.setEnabled(true);
        try {
            rateLimitFilter.init(new FilterConfig("DOSFilter", Map.of("delayMs", "0",
                    "maxRequestsPerSec", Integer.toString(maxRequestsPerSecond),
                    "throttledRequests", Integer.toString(burst),
                    "tooManyCode", "503")));
        } catch (ServletException e) {
            throw new DDPException("Could not initalize rate limits", e);
        }
        this.rateLimitFilter = rateLimitFilter;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        rateLimitFilter.doFilter(request.raw(), response.raw(), (req, res) -> { });
    }
}
