package org.broadinstitute.ddp.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.Assert;
import org.junit.Test;

public class NonSecureFilterTest {

    private NonSecureFilter secureFilter = new NonSecureFilter();

    @Test
    public void secureLogEntryIsExcluded() {
        LoggingEvent evt = new LoggingEvent();
        evt.setLoggerName(LogUtil.SECURE_LOGGER);
        Assert.assertEquals(FilterReply.DENY, secureFilter.decide(evt));
    }

    @Test
    public void insecureLogEntryIsIncluded() {
        LoggingEvent evt = new LoggingEvent();
        evt.setLoggerName("blah");
        Assert.assertEquals(FilterReply.ACCEPT, secureFilter.decide(evt));
    }
}
