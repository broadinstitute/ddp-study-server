package org.broadinstitute.ddp.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logging filter that accepts everything other
 * than loggers whose name is {@link LogUtil#SECURE_LOGGER}
 */
public class NonSecureFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent evt) {
        if (LogUtil.SECURE_LOGGER.equals(evt.getLoggerName())) {
            return FilterReply.DENY;
        } else {
            return FilterReply.ACCEPT;
        }
    }
}
