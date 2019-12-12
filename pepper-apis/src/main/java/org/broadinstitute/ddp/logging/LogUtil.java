package org.broadinstitute.ddp.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {

    public static final String SECURE_LOGGER = "DDP_SECURE";

    /**
     * Returns the logger whose read access is restricted to
     * folks who can read sensitive (PHI/PII) data
     */
    public static Logger getSecureLog() {
        return LoggerFactory.getLogger(SECURE_LOGGER);
    }

}
