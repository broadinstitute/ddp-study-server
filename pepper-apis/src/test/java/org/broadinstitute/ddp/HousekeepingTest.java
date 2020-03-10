package org.broadinstitute.ddp;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up {@link TransactionWrapper} for both {@link TransactionWrapper.DB#APIS} and
 * {@link TransactionWrapper.DB#HOUSEKEEPING} databases so that tests can
 * interact with both databases.
 *
 * <p>Also starts up {@link Housekeeping}, which will spawn the pubsub
 * emulator and shut it down at the end of the test.  Because
 * housekeeping is an async process, direct testing is a challenge.  We
 * can rely indirectly on log statements from housekeeping.  You can
 * call {@link #waitForLogging} to basically
 * grep the housekeeping logs looking for a particular log statement
 * and then check whether or not it was found in your asserts.
 */
public abstract class HousekeepingTest extends ConfigAwareBaseTest {

    public static final int LOG_WATCHER_TIMEOUT_MILLIS = 30 * 1000;

    private static final Logger LOG = LoggerFactory.getLogger(HousekeepingTest.class);

    private static Thread housekeepingThread;

    protected ch.qos.logback.classic.Logger loggingRoot = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch
            .qos.logback.classic.Logger.ROOT_LOGGER_NAME);

    protected static final Object logMonitor = new Object();

    private static void startHousekeepingMain() {
        // shorten sleep cycle so test doesn't stall and fail
        Housekeeping.SLEEP_MILLIS = 5 * 1000L;
        housekeepingThread = new Thread(() -> {
            Housekeeping.main(new String[] {});
        });
        housekeepingThread.setName("Housekeeping");
        housekeepingThread.start();
        synchronized (Housekeeping.startupMonitor) {
            try {
                Housekeeping.startupMonitor.wait(10 * 1000);
            } catch (InterruptedException e) {
                LOG.error("Housekeeping startup interrupted", e);
            }
        }
    }

    @BeforeClass
    public static void setupTransactionWrappersAndBootHousekeeping() {
        MySqlTestContainerUtil.initializeTestDbs();
        String pepperDbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
        int maxPepperConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String housekeepingDbUrl = cfg.getString(TransactionWrapper.DB.HOUSEKEEPING.getDbUrlConfigKey());
        int maxHousekeepingConnections = cfg.getInt(TransactionWrapper.DB.HOUSEKEEPING.getDbPoolSizeConfigKey());

        LiquibaseUtil.runLiquibase(pepperDbUrl, TransactionWrapper.DB.APIS);
        LiquibaseUtil.runLiquibase(housekeepingDbUrl, TransactionWrapper.DB.HOUSEKEEPING);

        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                        maxPepperConnections, pepperDbUrl),
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.HOUSEKEEPING,
                        maxHousekeepingConnections, housekeepingDbUrl));

        DBUtils.loadDaoSqlCommands(sqlConfig);
        startHousekeepingMain();
    }

    @AfterClass
    public static void stopHousekeepingMain() {
        Housekeeping.stop();
        if (housekeepingThread != null) {
            try {
                housekeepingThread.join(Housekeeping.SLEEP_MILLIS);
            } catch (InterruptedException e) {
                LOG.error("Interrupted while shutting down housekeeping thread ", e);
            }
        }
        TransactionWrapper.reset();
    }

    /**
     * Blocks for up to {@link #LOG_WATCHER_TIMEOUT_MILLIS} waiting for
     * the given message to appear in the log via {@link String#contains(CharSequence)}.
     *
     * @return true of the log message was found in the housekeeping logs, false otherwise.
     */
    protected boolean waitForLogging(String expectedMessage) throws InterruptedException {
        LogWatcher<ILoggingEvent> logWatcher = setupLogWatcher(expectedMessage);
        Thread logWaiterThread = new Thread(() -> {
            synchronized (logMonitor) {
                try {
                    logMonitor.wait(LOG_WATCHER_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    LOG.error("log waiter interrupted", e);
                }
            }
        });
        logWaiterThread.start();
        logWaiterThread.join();
        loggingRoot.detachAppender(logWatcher);
        return logWatcher.wasLogEntryFound();
    }

    /**
     * Watches logs, looking for an expected log entry.
     * When found, notifies {@link #logMonitor}
     */
    private class LogWatcher<E> extends AppenderBase<E> {

        private final String expectedLogEntry;

        private boolean logEntryMatched;

        public LogWatcher(String expectedLogEntry) {
            this.expectedLogEntry = expectedLogEntry;
        }

        @Override
        protected void append(E e) {
            if (e.toString().contains(expectedLogEntry)) {
                logEntryMatched = true;
                synchronized (logMonitor) {
                    logMonitor.notify();
                }
            }
        }

        public boolean wasLogEntryFound() {
            return logEntryMatched;
        }
    }

    /**
     * Configures a {@link LogWatcher} with the given
     * expected log entry, starts it, and adds it to the
     * log root
     */
    private LogWatcher<ILoggingEvent> setupLogWatcher(String expectedLogEntry) {
        LogWatcher<ILoggingEvent> logWatcher = new LogWatcher<>(expectedLogEntry);
        logWatcher.start();
        loggingRoot.addAppender(logWatcher);
        return logWatcher;
    }
}
