package org.broadinstitute.ddp.handlers.util;


import org.broadinstitute.ddp.util.CheckValidity;

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
