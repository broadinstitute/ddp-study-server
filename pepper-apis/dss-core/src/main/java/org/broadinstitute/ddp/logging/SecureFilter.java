package org.broadinstitute.ddp.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logging filter that rejects everything
 * unless the logger's name is {@link LogUtil#SECURE_LOGGER}
 */
public class SecureFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent evt) {
        if (LogUtil.SECURE_LOGGER.equals(evt.getLoggerName())) {
            return FilterReply.ACCEPT;
        } else {
            return FilterReply.DENY;
        }
    }
}
