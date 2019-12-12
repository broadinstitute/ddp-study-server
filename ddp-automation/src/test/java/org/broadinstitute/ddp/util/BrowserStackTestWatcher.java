package org.broadinstitute.ddp.util;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserStackTestWatcher extends TestWatcher {

    private static final Logger logger = LoggerFactory.getLogger(BrowserStackTestWatcher.class);

    private final BrowserStackUtil browserStackUtil;

    private final OnTestStartHandler onTestStartHandler;

    private final AtomicReference<String> sessionIdAccessor;

    private final BuildStatusUpdateHandler buildStatusUpdateHandler;

    /**
     * Creates a new watcher
     *
     * @param util                     browserstack utility
     * @param onTestStart              called when each test starts
     * @param sessionIdAccessor        used to track a changing sessionId
     * @param buildStatusUpdateHandler called when this class
     *                                 successfully updates the status of the session
     *                                 at browserstack
     */
    public BrowserStackTestWatcher(BrowserStackUtil util,
                                   OnTestStartHandler onTestStart,
                                   AtomicReference<String> sessionIdAccessor,
                                   BuildStatusUpdateHandler buildStatusUpdateHandler) {
        this.browserStackUtil = util;
        this.onTestStartHandler = onTestStart;
        this.sessionIdAccessor = sessionIdAccessor;
        this.buildStatusUpdateHandler = buildStatusUpdateHandler;
    }

    private boolean hasSessionId() {
        return StringUtils.isNotBlank(sessionIdAccessor.get());
    }

    @Override
    protected void failed(Throwable e, Description description) {
        if (hasSessionId()) {
            Throwable cause = e;
            if (e.getCause() != null) {
                // JDI/webdriver often have useful information in the cause, one level down
                cause = e.getCause();
            }
            browserStackUtil.markBuildStatus(sessionIdAccessor.get(), false, cause.getMessage());
            buildStatusUpdateHandler.buildStatusUpdated();
        }
        logger.info("{} has failed: ", description.getDisplayName(), e.getMessage());
        e.printStackTrace();
    }

    @Override
    protected void succeeded(Description description) {
        if (hasSessionId()) {
            browserStackUtil.markBuildStatus(sessionIdAccessor.get(), true, "");
            buildStatusUpdateHandler.buildStatusUpdated();
        }
        logger.info("{} passed", description.getDisplayName());
    }

    @Override
    protected void starting(Description description) {
        DisplayName displayName = description.getAnnotation(DisplayName.class);
        String currentTestName = null;
        if (displayName != null) {
            currentTestName = displayName.value();
        } else {
            currentTestName = description.getMethodName();
        }
        onTestStartHandler.testStarted(currentTestName);
        logger.info("Starting {}", currentTestName);
    }

    @FunctionalInterface
    public interface OnTestStartHandler {
        void testStarted(String testName);
    }

    @FunctionalInterface
    public interface BuildStatusUpdateHandler {
        void buildStatusUpdated();
    }
}
