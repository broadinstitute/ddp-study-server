package org.broadinstitute.ddp.filter;

import org.slf4j.MDC;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Filter that removes a set of attributes.  Should be used
 * via {@link spark.Spark#afterAfter}.
 */
public class MDCAttributeRemovalFilter implements Filter {

    private final String[] attributesToRemove;

    public MDCAttributeRemovalFilter(String...attributesToRemove) {
        this.attributesToRemove = attributesToRemove;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        for (String attributeToRemove : attributesToRemove) {
            MDC.remove(attributeToRemove);
        }
    }
}
