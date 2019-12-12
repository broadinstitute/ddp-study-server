package org.broadinstitute.ddp;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.sendgrid.SendGrid;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiMessageDestination;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.housekeeping.dao.JdbiEvent;
import org.broadinstitute.ddp.db.housekeeping.dao.JdbiMessage;
import org.broadinstitute.ddp.db.housekeeping.dao.KitCheckDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.MessageBuilderException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.broadinstitute.ddp.housekeeping.PubSubMessageBuilder;
import org.broadinstitute.ddp.housekeeping.handler.EmailNotificationHandler;
import org.broadinstitute.ddp.housekeeping.handler.MessageHandlingException;
import org.broadinstitute.ddp.housekeeping.handler.MissingUserException;
import org.broadinstitute.ddp.housekeeping.handler.PdfGenerationHandler;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessage;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.housekeeping.message.PdfGenerationMessage;
import org.broadinstitute.ddp.housekeeping.schedule.DataSyncJob;
import org.broadinstitute.ddp.housekeeping.schedule.DatabaseBackupCheckJob;
import org.broadinstitute.ddp.housekeeping.schedule.DatabaseBackupJob;
import org.broadinstitute.ddp.housekeeping.schedule.StudyExportToBucketJob;
import org.broadinstitute.ddp.housekeeping.schedule.StudyExportToESJob;
import org.broadinstitute.ddp.housekeeping.schedule.TemporaryUserCleanupJob;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.schedule.JobScheduler;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.LogbackConfigurationPrinter;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Housekeeping {

    public static final AtomicBoolean startupMonitor = new AtomicBoolean();

    private static final Map<Object, Long> lastLogTimeForException = new HashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(Housekeeping.class);

    /**
     * Key is study guid, value is the metrics transmitter for the study
     */
    private static final Map<String, StackdriverMetricsTracker> kitCounterMonitorByStudy = new HashMap<>();

    private static final String DDP_ATTRIBUTE_PREFIX = "ddp.";
    /**
     * Version of housekeeping that created this message.
     * May be useful in the future when we need to support
     * concurrent processing of queued messages that use
     * older message formats.
     */
    public static final String DDP_HOUSEKEEPING_VERSION = DDP_ATTRIBUTE_PREFIX + "housekeepingVersion";
    /**
     * Internal housekeeping event id for this message
     */
    public static final String DDP_EVENT_ID = DDP_ATTRIBUTE_PREFIX + "eventId";
    /**
     * Event type of this message, necessary to help receivers
     * interpret the message body appropriately.
     */
    public static final String DDP_EVENT_TYPE = DDP_ATTRIBUTE_PREFIX + "eventType";
    /**
     * Internal housekeeping message id
     */
    public static final String DDP_MESSAGE_ID = DDP_ATTRIBUTE_PREFIX + "messageId";
    /**
     * Study from whence the message came.  Will be needed for dispatching
     * events to third-party integration points.
     */
    public static final String DDP_STUDY_GUID = DDP_ATTRIBUTE_PREFIX + "studyGuid";
    /**
     * The maximum number of instances of an event to respond to
     */
    public static final String DDP_IGNORE_AFTER = DDP_ATTRIBUTE_PREFIX + "maxOccurences";
    /**
     * Internal event_configuration_id that this message was spawned for
     */
    public static final String DDP_EVENT_CONFIGURATION_ID = DDP_ATTRIBUTE_PREFIX + "eventConfigurationId";
    /**
     * Fixed thread pool for executing pubsub success/failure
     * callbacks during publishing
     */
    private static final Executor PUBSUB_PUBLISH_CALLBACK_EXECUTOR = Executors.newFixedThreadPool(10);
    private static final AtomicBoolean afterHandlerGuard = new AtomicBoolean();
    public static final String IGNORE_EVENT_LOG_MESSAGE = "Ignoring and removing occurrence of event ";

    /**
     * How many milliseconds to wait between writing error log entry
     * for the same exception
     */
    private static final long ERROR_LOG_QUIET_TIME = 30 * 60 * 1000;

    public static long SLEEP_MILLIS = 30 * 1000L;
    /**
     * Whether or not to keep running housekeeping
     */
    private static boolean stop;
    /**
     * Primarily used for testing, this is a callback
     * that runs after a message is handled
     */
    private static AfterHandlerCallback afterHandling;

    public static void setAfterHandler(AfterHandlerCallback afterHandler) {
        synchronized (afterHandlerGuard) {
            afterHandling = afterHandler;
        }
    }

    public static void clearAfterHandler() {
        synchronized (afterHandlerGuard) {
            afterHandling = null;
        }
    }

    public static void main(String[] args) {
        LogbackConfigurationPrinter.printLoggingConfiguration();
        Config cfg = ConfigManager.getInstance().getConfig();
        boolean doLiquibase = cfg.getBoolean(ConfigFile.DO_LIQUIBASE);
        int maxConnections = cfg.getInt(ConfigFile.HOUSEKEEPING_NUM_POOLED_CONNECTIONS);
        String pubSubProject = cfg.getString(ConfigFile.PUBSUB_PROJECT);

        boolean usePubSubEmulator = cfg.getBoolean(ConfigFile.USE_PUBSUB_EMULATOR);
        String housekeepingDbUrl = cfg.getString(TransactionWrapper.DB.HOUSEKEEPING.getDbUrlConfigKey());
        String apisDbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());

        Config sqlConfig = ConfigFactory.load(ConfigFile.SQL_CONFIG_FILE);
        DBUtils.loadDaoSqlCommands(sqlConfig);
        KitCheckDao kitCheckDao = new KitCheckDao();

        if (!TransactionWrapper.isInitialized()) {
            TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.HOUSEKEEPING,
                            maxConnections, housekeepingDbUrl),
                    new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                            maxConnections, apisDbUrl));
        }

        if (doLiquibase) {
            // we only migrate the housekeeping db when starting
            LiquibaseUtil.runLiquibase(housekeepingDbUrl, TransactionWrapper.DB.HOUSEKEEPING);
        }

        boolean runScheduler = cfg.getBoolean(ConfigFile.RUN_SCHEDULER);
        Scheduler scheduler = null;

        if (runScheduler) {
            LOG.info("Booting job scheduler...");
            scheduler = JobScheduler.initializeWith(cfg,
                    DataSyncJob::register,
                    DatabaseBackupJob::register,
                    DatabaseBackupCheckJob::register,
                    TemporaryUserCleanupJob::register,
                    StudyExportToESJob::register,
                    StudyExportToBucketJob::register);
        } else {
            LOG.info("Housekeeping job scheduler is not set to run");
        }

        final PubSubConnectionManager pubsubConnectionManager = new PubSubConnectionManager(usePubSubEmulator);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            JdbiMessageDestination messageDestinationDao = handle.attach(JdbiMessageDestination.class);
            for (String topicName : messageDestinationDao.getAllTopics()) {
                LOG.info("Initializing subscription for topic {}", topicName);
                ProjectTopicName projectTopicName = ProjectTopicName.of(pubSubProject, topicName);
                pubsubConnectionManager.createTopicIfNotExists(projectTopicName);
                // todo arz investigate topic naming vs. subscription naming
                ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(
                        projectTopicName.getProject(), topicName);
                Subscription subscription = pubsubConnectionManager
                        .createSubscriptionIfNotExists(projectSubscriptionName, projectTopicName);
                // in the real world, listen differently
                setupMessageReceiver(pubsubConnectionManager, projectSubscriptionName, cfg);
            }
        });

        synchronized (startupMonitor) {
            startupMonitor.notify();
        }

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        PubSubMessageBuilder messageBuilder = new PubSubMessageBuilder(cfg);
        StackdriverMetricsTracker heartbeatMonitor = null;

        heartbeatMonitor = new StackdriverMetricsTracker(StackdriverCustomMetric.HOUSEKEEPING_CYCLES,
                PointsReducerFactory.buildMaxPointReducer());

        //loop to pickup pending events on main DB API and create messages to send over to Housekeeping
        while (!stop) {
            try {
                TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                    KitCheckDao.KitCheckResult kitCheckResult = kitCheckDao.checkForKits(handle);
                    sendKitMetrics(kitCheckResult);
                });
            } catch (Exception e) {
                LOG.error("Could not setup kits", e);
            }

            try {
                int numEventsProcessed = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                    EventDao eventDao = apisHandle.attach(EventDao.class);
                    JdbiQueuedEvent queuedEventDao = apisHandle.attach(JdbiQueuedEvent.class);
                    // first query the full list of pending events, shuffled to avoid event starvation
                    LOG.info("Querying pending events");
                    List<QueuedEventDto> pendingEvents = eventDao.getQueuedEvents();
                    LOG.info("Found {} events that may be publishable", pendingEvents.size());
                    Collections.shuffle(pendingEvents);

                    for (QueuedEventDto pendingEvent : pendingEvents) {
                        String pendingEventId = pendingEvent.getDdpEventId();
                        boolean shouldCancel = false;
                        if (StringUtils.isNotBlank(pendingEvent.getCancelCondition())) {
                            try {
                                shouldCancel = pexInterpreter.eval(pendingEvent.getCancelCondition(),
                                        apisHandle,
                                        pendingEvent.getParticipantGuid(),
                                        null);
                            } catch (PexException e) {
                                LOG.warn("Failed to evaluate cancelCondition pex, defaulting to false: `{}`",
                                        pendingEvent.getCancelCondition(), e);
                                shouldCancel = false;
                            }
                        }
                        if (shouldCancel) {
                            LOG.info("Deleting queued event {} because its cancel condition has been met", pendingEvent
                                    .getQueuedEventId());
                            int rowsDeleted = queuedEventDao.deleteAllByQueuedEventId(pendingEvent.getQueuedEventId());
                            if (rowsDeleted != 1) {
                                throw new DDPException("Deleted " + rowsDeleted + " rows for queued event "
                                        + pendingEvent.getQueuedEventId() + " after hitting cancel "
                                        + "condition "
                                        + pendingEvent.getCancelCondition());
                            }
                        } else {
                            boolean hasMetPrecondition = true;
                            if (StringUtils.isNotBlank(pendingEvent.getPrecondition())) {
                                try {
                                    hasMetPrecondition = pexInterpreter.eval(pendingEvent.getPrecondition(),
                                            apisHandle,
                                            pendingEvent.getParticipantGuid(),
                                            null);
                                } catch (PexException e) {
                                    LOG.warn("Failed to evaluate precondition pex, defaulting to false: `{}`",
                                            pendingEvent.getPrecondition(), e);
                                    hasMetPrecondition = false;
                                }
                            }

                            if (hasMetPrecondition) {
                                TransactionWrapper.useTxn(TransactionWrapper.DB.HOUSEKEEPING, housekeepingHandle -> {
                                    JdbiEvent jdbiEvent = housekeepingHandle.attach(JdbiEvent.class);
                                    JdbiMessage jdbiMessage = housekeepingHandle.attach(JdbiMessage.class);
                                    if (jdbiEvent.shouldHandleEvent(
                                            pendingEventId,
                                            pendingEvent.getActionType().name(),
                                            pendingEvent.getMaxOccurrencesPerUser())) {
                                        String ddpMessageId = Long.toString(jdbiMessage.insertMessageForEvent(
                                                pendingEventId));
                                        LOG.info("Publishing queued event {}", pendingEvent.getQueuedEventId());
                                        PubsubMessage message = null;
                                        try {
                                            message = messageBuilder.createMessage(ddpMessageId, pendingEvent, apisHandle);
                                        } catch (MessageBuilderException e) {
                                            LOG.error("Could not create message for queued event "
                                                    + pendingEvent.getQueuedEventId(), e);
                                        }

                                        if (message != null) {
                                            // publish the message and delete it from the queue when published
                                            Publisher publisher = null;
                                            // todo arz cache publishers, creating them is expensive
                                            try {
                                                publisher = pubsubConnectionManager.createPublisher(ProjectTopicName.of(
                                                        pubSubProject, pendingEvent.getPubSubTopic()));
                                            } catch (IOException e) {
                                                throw new DDPException("Could not create publisher for " + pendingEvent
                                                        .getPubSubTopic(), e);
                                            }
                                            ApiFuture<String> publishResult = publisher.publish(message);

                                            int numRowsUpdated = queuedEventDao.markPending(pendingEvent.getQueuedEventId());
                                            LOG.info("Marked queued event {} as pending", pendingEvent.getQueuedEventId());
                                            if (numRowsUpdated != 1) {
                                                throw new DaoException("Marked " + numRowsUpdated + " rows as pending for "

                                                        + "queued event " + pendingEventId);
                                            }
                                            setPostPublishingCallbacks(publishResult, pendingEvent.getQueuedEventId());
                                        } else {
                                            LOG.error("null message for " + pendingEvent.getQueuedEventId());
                                        }
                                    } else {
                                        LOG.info(IGNORE_EVENT_LOG_MESSAGE + "{}", pendingEvent
                                                .getEventConfigurationId());
                                        synchronized (afterHandlerGuard) {
                                            if (afterHandling != null) {
                                                String eventIdStr = Long.toString(pendingEvent.getEventConfigurationId());
                                                afterHandling.eventIgnored(eventIdStr);
                                            }
                                        }
                                        queuedEventDao.deleteAllByQueuedEventId(pendingEvent.getQueuedEventId());
                                    }
                                });
                            } else {
                                LOG.info("Skipping event {} because its precondition has not been met", pendingEvent
                                        .getQueuedEventId());
                            }
                        }
                    }
                    return pendingEvents.size();
                });

            } catch (Exception e) {
                logException(e);
            }
            // send a ping to monitoring
            heartbeatMonitor.addPoint(1, Instant.now().toEpochMilli());

            try {
                Thread.sleep(SLEEP_MILLIS);
            } catch (InterruptedException e) {
                LOG.info("Housekeeping interrupted during sleep", e);
            }
        }
        pubsubConnectionManager.close();
        if (scheduler != null) {
            JobScheduler.shutdownScheduler(scheduler, true);
        }
        LOG.info("Housekeeping is shutting down");
    }

    private static void sendKitMetrics(KitCheckDao.KitCheckResult kitCheckResult) {
        for (Map.Entry<String, AtomicInteger> queuedParticipantsByStudy : kitCheckResult.getQueuedParticipantsByStudy()) {
            String study = queuedParticipantsByStudy.getKey();
            int numQueuedParticipants = queuedParticipantsByStudy.getValue().get();
            if (!kitCounterMonitorByStudy.containsKey(study)) {
                kitCounterMonitorByStudy.put(study, new StackdriverMetricsTracker(StackdriverCustomMetric.KITS_REQUESTED,
                        study, PointsReducerFactory.buildSumReducer()));
            }
            kitCounterMonitorByStudy.get(study).addPoint(numQueuedParticipants, Instant.now().toEpochMilli());
        }
    }

    private static boolean isTimeForLogging(long lastLogTime) {
        return Instant.now().toEpochMilli() - lastLogTime > ERROR_LOG_QUIET_TIME;
    }

    private static boolean shouldLog(Exception e) {
        boolean shouldLog = true;
        if (lastLogTimeForException.containsKey(e)) {
            long lastLogTime = lastLogTimeForException.get(e);
            shouldLog = isTimeForLogging(lastLogTime);
        }
        return shouldLog;
    }

    private static void logException(Exception e) {
        if (shouldLog(e)) {
            LOG.error("Housekeeping error", e);
            lastLogTimeForException.replace(e, Instant.now().toEpochMilli());
        }
    }

    public static void stop() {
        stop = true;
    }

    /**
     * Sets up post-publish callbacks.  When the event is published
     * successfully, it's deleted from the queue.  If there is an error
     * during publishing, the event is requeued.
     */
    private static void setPostPublishingCallbacks(ApiFuture future, long queuedEventId) {
        ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
            @Override
            public void onFailure(Throwable t) {
                PUBSUB_PUBLISH_CALLBACK_EXECUTOR.execute(() -> {
                    if (t instanceof ApiException) {
                        ApiException apiException = ((ApiException) t);
                        LOG.error("Error while publishing queued event {}.  Pubsub returned {}", queuedEventId,
                                apiException.getStatusCode(), apiException);
                    }
                    LOG.error("Error while publishing queued event {}.  Re-queueing.", queuedEventId, t);
                    TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                        JdbiQueuedEvent queuedEventDao = apisHandle.attach(JdbiQueuedEvent.class);
                        int numRowsUpdated = queuedEventDao.clearStatus(queuedEventId);
                        if (numRowsUpdated != 1) {
                            throw new DaoException("Updated " + numRowsUpdated + " rows for queued event "
                                    + queuedEventId);
                        }
                    });
                });
            }

            @Override
            public void onSuccess(String messageId) {
                PUBSUB_PUBLISH_CALLBACK_EXECUTOR.execute(() -> {
                    LOG.info("Posted queued event {} with message id {}", queuedEventId, messageId);
                    TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                        LOG.info("Attempting to delete queued event {}", queuedEventId);
                        int numRowsDeleted = queuedEventDao.deleteByQueuedEventId(queuedEventId);
                        if (numRowsDeleted != 1) {
                            throw new DaoException("Deleted " + numRowsDeleted + " rows for queued event "
                                    + queuedEventId);
                        } else {
                            LOG.info("Deleted posted event {}", queuedEventId);
                        }
                    });
                });
            }
        });
    }

    private static void setupMessageReceiver(PubSubConnectionManager pubsubConnectionManager, ProjectSubscriptionName
            projectSubscriptionName, Config cfg) {
        PdfService pdfService = new PdfService();
        PdfBucketService pdfBucketService = new PdfBucketService(cfg);
        PdfGenerationService pdfGenerationService = new PdfGenerationService();
        PdfGenerationHandler pdfGenerationHandler = new PdfGenerationHandler(pdfService, pdfBucketService, pdfGenerationService);
        Gson gson = new Gson();
        try {
            MessageReceiver receiver =
                    new MessageReceiver() {
                        @Override
                        public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
                            try {
                                String ddpMessageId = message.getAttributesOrThrow(DDP_MESSAGE_ID);
                                String ddpEventConfigurationId = message.getAttributesOrThrow(DDP_EVENT_CONFIGURATION_ID);
                                String ddpEventId = message.getAttributesOrThrow(Housekeeping.DDP_EVENT_ID);
                                String ddpEventType = message.getAttributesOrThrow(Housekeeping.DDP_EVENT_TYPE);
                                String ddpIgnoreAfterStr = message.getAttributesOrDefault(DDP_IGNORE_AFTER, null);
                                AtomicReference<Integer> ddpIgnoreAfter = new AtomicReference<>();
                                if (StringUtils.isNotBlank(ddpIgnoreAfterStr)) {
                                    ddpIgnoreAfter.set(Integer.parseInt(ddpIgnoreAfterStr));
                                }
                                TransactionWrapper.useTxn(TransactionWrapper.DB.HOUSEKEEPING, handle -> {
                                    JdbiMessage jdbiMessage = handle.attach(JdbiMessage.class);
                                    JdbiEvent jdbiEvent = handle.attach(JdbiEvent.class);
                                    //Check for message for duplicate message id. Handle only once
                                    if (jdbiMessage.shouldProcessMessage(ddpMessageId)) {
                                        String messageText = message.getData().toStringUtf8();
                                        Map<String, String> attributes = message.getAttributesMap();
                                        LOG.info("Received ddp message {} of type/version {}/{} for event {} with text {}",
                                                attributes.get(DDP_MESSAGE_ID),
                                                attributes.get(Housekeeping.DDP_EVENT_TYPE),
                                                attributes.get(DDP_HOUSEKEEPING_VERSION),
                                                ddpEventId,
                                                messageText);

                                        HousekeepingMessage messageContents;
                                        if (EventActionType.NOTIFICATION.name().equals(ddpEventType)) {
                                            NotificationMessage notificationMessage = gson.fromJson(
                                                    message.getData().toStringUtf8(), NotificationMessage.class);
                                            messageContents = notificationMessage;
                                            String participantGuid = notificationMessage.getParticipantGuid();
                                            if (StringUtils.isNotBlank(participantGuid) && !checkUserExists(participantGuid)) {
                                                throw new MissingUserException("Error processing notification message, "
                                                        + "GUID " + notificationMessage.getParticipantGuid()
                                                        + " is not in the pepper database");
                                            }

                                            new EmailNotificationHandler(new SendGrid(notificationMessage.getApiKey()),
                                                    pdfService, pdfBucketService, pdfGenerationService)
                                                    .handleMessage(notificationMessage);

                                        } else if (EventActionType.PDF_GENERATION.name().equals(ddpEventType)) {
                                            PdfGenerationMessage pdfGenerationMessage = gson.fromJson(
                                                    message.getData().toStringUtf8(), PdfGenerationMessage.class);
                                            messageContents = pdfGenerationMessage;
                                            String participantGuid = pdfGenerationMessage.getParticipantGuid();
                                            if (StringUtils.isNotBlank(participantGuid) && !checkUserExists(participantGuid)) {
                                                throw new MissingUserException("Error processing pdf generation message, "
                                                        + "GUID " + pdfGenerationMessage.getParticipantGuid()
                                                        + " is not in the pepper database");
                                            }

                                            pdfGenerationHandler.handleMessage(pdfGenerationMessage);
                                        } else {
                                            consumer.nack(); // message not handled; pubsub should retry it
                                            throw new DDPException("Cannot handle event action type " + ddpEventType);
                                        }

                                        synchronized (afterHandlerGuard) {
                                            if (afterHandling != null) {
                                                afterHandling.messageHandled(messageContents, ddpEventConfigurationId);
                                            }
                                        }
                                        jdbiMessage.markMessageAsProcessed(ddpMessageId);
                                        jdbiEvent.incrementOccurrencesProcessed(ddpEventId);
                                        consumer.ack(); // message fully processed
                                    } else {
                                        consumer.ack(); // deliberate decision to *not* handle duplicated message, so
                                        // do not redeliver
                                        LOG.warn("Skipping duplicate message {} for event {}", ddpMessageId,
                                                ddpEventId);
                                    }
                                });
                            } catch (MissingUserException e) {
                                LOG.error("We have a message for which we no longer have a user, "
                                        + "ack-ing and skipping it: ", e);
                            } catch (MessageHandlingException e) {
                                LOG.error("Trouble processing message", e);
                                if (e.shouldRetry()) {
                                    consumer.nack();
                                } else {
                                    consumer.ack();
                                }
                            } catch (Exception e) {
                                consumer.nack();
                                LOG.error("Could not make sense of message " + message.getMessageId(), e);
                            }
                        }
                    };

            Subscriber subscriber = pubsubConnectionManager.subscribe(projectSubscriptionName, receiver);
            subscriber.startAsync();
        } catch (Exception e) {
            LOG.error("Error during message handling", e);
        }
    }

    private static boolean checkUserExists(String participantGuid) {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, apihandle -> {
            UserDto userDto = apihandle.attach(JdbiUser.class).findByUserGuid(participantGuid);

            return userDto != null;
        });
    }

    /**
     * Callback fired after a message is
     * handled.  Primarily used for testing.
     */
    public interface AfterHandlerCallback {

        /**
         * Method runs immediately after a message is
         * handled
         *
         * @param message              the message
         * @param eventConfigurationId the event_configuration_id for the event
         */
        void messageHandled(HousekeepingMessage message, String eventConfigurationId);

        /**
         * Method runs after an event is ignored
         *
         * @param eventConfigurationId id of the event configuration
         */
        void eventIgnored(String eventConfigurationId);
    }
}
