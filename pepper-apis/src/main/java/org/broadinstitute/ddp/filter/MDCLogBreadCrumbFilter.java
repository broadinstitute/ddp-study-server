package org.broadinstitute.ddp.filter;

import java.util.UUID;

import org.slf4j.MDC;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Filter that puts a unique-ish value into MDC so that
 * you can more easily grep the logs to find a particular entry
 */
public class MDCLogBreadCrumbFilter implements Filter {

    public static final String LOG_BREADCRUMB = "LogBreadCrumb";

    @Override
    public void handle(Request request, Response response) throws Exception {
        MDC.put(LOG_BREADCRUMB, generateBreadCrumb());
    }

    private String generateBreadCrumb() {
        return UUID.randomUUID().toString();
    }
}
