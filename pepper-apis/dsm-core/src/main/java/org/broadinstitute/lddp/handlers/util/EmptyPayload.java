package org.broadinstitute.lddp.handlers.util;


import org.broadinstitute.lddp.util.CheckValidity;

/**
 * used in AbstractRequestHandler
 * In cases we do not need to parse the body of the request, like a GET request
 */
public class EmptyPayload implements CheckValidity {

    @Override
    public boolean isValid() {
        return true;
    }
}
