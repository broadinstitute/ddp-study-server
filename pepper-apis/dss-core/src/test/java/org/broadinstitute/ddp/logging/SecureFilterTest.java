package org.broadinstitute.ddp.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.Assert;
import org.junit.Test;

public class SecureFilterTest {

    private SecureFilter secureFilter = new SecureFilter();

    @Test
    public void secureLogEntryIsIncluded() {
        LoggingEvent evt = new LoggingEvent();
        evt.setLoggerName(LogUtil.SECURE_LOGGER);
        Assert.assertEquals(FilterReply.ACCEPT, secureFilter.decide(evt));
    }

    @Test
    public void insecureLogEntryIsDenied() {
        LoggingEvent evt = new LoggingEvent();
        evt.setLoggerName("blah");
        Assert.assertEquals(FilterReply.DENY, secureFilter.decide(evt));
    }
}
