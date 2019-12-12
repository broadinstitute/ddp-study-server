package org.broadinstitute.ddp.filter;

import org.slf4j.MDC;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Filter that takes a given HTTP header and adds it
 * to {@link MDC} using the header as the key.  Should be used
 * via {@link spark.Spark#before}.
 */
public class HttpHeaderMDCFilter implements Filter {

    private final String[] headerNames;

    public HttpHeaderMDCFilter(String...httpHeaderNames) {
        headerNames = httpHeaderNames;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        for (String headerName : headerNames) {
            MDC.put(headerName, request.headers(headerName));
        }
    }
}
