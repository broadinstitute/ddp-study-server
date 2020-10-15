package org.broadinstitute.ddp.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.broadinstitute.ddp.util.RedisConnectionValidator;

public class RedisConnectionValidatorAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
        RedisConnectionValidator.doTest();
    }
}
