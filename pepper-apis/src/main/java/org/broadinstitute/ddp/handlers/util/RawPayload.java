package org.broadinstitute.ddp.handlers.util;


import lombok.Data;
import org.broadinstitute.ddp.util.CheckValidity;
@Data
/**
 * used in AbstractRequestHandler
 * In cases we do not want to parse the body of the request before it gets to our handler, like for a "lite" DDP
 */
public class RawPayload implements CheckValidity {
    private String value;

    public RawPayload(String rawPayload) {
        value = rawPayload;
    }

    public boolean isValid() {
        return true;
    }
}
