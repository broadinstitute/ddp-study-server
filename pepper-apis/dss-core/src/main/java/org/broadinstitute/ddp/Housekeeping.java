package org.broadinstitute.ddp;

import static org.broadinstitute.ddp.constants.ConfigFile.PUBSUB_TASKS_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskConnectionService.DEFAULT_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT_SEC;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.core.ApiService;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.Subscription;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.appengine.spark.SparkBootUtil;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.customexport.housekeeping.schedule.CustomExportJob;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiMessageDestination;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.housekeeping.dao.JdbiEvent;
import org.broadinstitute.ddp.db.housekeeping.dao.JdbiMessage;
import org.broadinstitute.ddp.event.FileScanResultReceiver;
import org.broadinstitute.ddp.event.HousekeepingTaskReceiver;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskConnectionService;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.broadinstitute.ddp.event.pubsubtask.impl.PubSubTaskProcessorFactoryImpl;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.MessageBuilderException;
import org.broadinstitute.ddp.exception.NoSendableEmailAddressException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.broadinstitute.ddp.housekeeping.PubSubMessageBuilder;
import org.broadinstitute.ddp.housekeeping.handler.EmailNotificationHandler;
import org.broadinstitute.ddp.housekeeping.handler.MessageHandlingException;
import org.broadinstitute.ddp.housekeeping.handler.MissingUserException;
import org.broadinstitute.ddp.housekeeping.handler.PdfGenerationHandler;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessage;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.housekeeping.message.PdfGenerationMessage;
import org.broadinstitute.ddp.housekeeping.schedule.CheckAgeUpJob;
import org.broadinstitute.ddp.housekeeping.schedule.CheckKitsJob;
import org.broadinstitute.ddp.housekeeping.schedule.DataSyncJob;
import org.broadinstitute.ddp.housekeeping.schedule.DatabaseBackupCheckJob;
import org.broadinstitute.ddp.housekeeping.schedule.DatabaseBackupJob;
import org.broadinstitute.ddp.housekeeping.schedule.FileUploadCleanupJob;
import org.broadinstitute.ddp.housekeeping.schedule.FileUploadNotificationJob;
import org.broadinstitute.ddp.housekeeping.schedule.OnDemandExportJob;
import org.broadinstitute.ddp.housekeeping.schedule.StudyDataExportJob;
import org.broadinstitute.ddp.housekeeping.schedule.TemporaryUserCleanupJob;
import org.broadinstitute.ddp.logging.LogUtil;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.CreateKitEventAction;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.event.UpdateUserStatusEventAction;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.broadinstitute.ddp.model.user.User;
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
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.LogbackConfigurationPrinter;
import org.jdbi.v3.core.Handle;
import org.quartz.Scheduler;
import spark.Spark;

@Slf4j
public class Housekeeping {

    public static final AtomicBoolean startupMonitor = new AtomicBoolean();

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
    private static final String IGNORE_EVENT_LOG_MESSAGE = "Ignoring and removing occurrence of event ";

    private static final String ENV_PORT = "PORT";

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

    private static Scheduler scheduler;
    private static Subscriber taskSubscriber;
    private static Subscriber fileScanResultSubscriber;
    private static PubSubConnectionManager pubsubConnectionManager;
    private static PubSubTaskConnectionService pubSubTaskConnectionService;

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
        LogUtil.addAppEngineEnvVarsToMDC();
        // respond GAE dispatcher endpoints as soon as possible
        SparkBootUtil.startSparkServer(new SparkBootUtil.AppEngineShutdown() {
            @Override
            public void onAhStop() {
                log.info("Shutting down housekeeping instance {}", LogUtil.getAppEngineInstance());
                shutdown();
            }

            @Override
            public void onTerminate() {
                log.info("Terminating housekeeping instance {}", LogUtil.getAppEngineInstance());
                shutdown();
            }
        }, ConfigManager.getInstance().getConfig());
        start(args, null);
    }

    public static void start(String[] args, SendGridSupplier sendGridSupplier) {
        Config cfg = ConfigManager.getInstance().getConfig();
        boolean doLiquibase = cfg.getBoolean(ConfigFile.DO_LIQUIBASE);
        int maxConnections = cfg.getInt(ConfigFile.HOUSEKEEPING_NUM_POOLED_CONNECTIONS);
        String pubSubProject = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);

        boolean usePubSubEmulator = cfg.getBoolean(ConfigFile.USE_PUBSUB_EMULATOR);
        String housekeepingDbUrl = cfg.getString(TransactionWrapper.DB.HOUSEKEEPING.getDbUrlConfigKey());
        String apisDbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());

        Config sqlConfig = ConfigFactory.load(ConfigFile.SQL_CONFIG_FILE);
        DBUtils.loadDaoSqlCommands(sqlConfig);

        if (!TransactionWrapper.isInitialized()) {
            TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.HOUSEKEEPING,
                            maxConnections, housekeepingDbUrl),
                    new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS,
                            maxConnections, apisDbUrl));
        }

        if (doLiquibase) {
            log.info("Running Pepper liquibase migrations against " + apisDbUrl);
            LiquibaseUtil.runLiquibase(apisDbUrl, TransactionWrapper.DB.APIS);
            log.info("Running Housekeeping liquibase migrations against " + housekeepingDbUrl);
            LiquibaseUtil.runLiquibase(housekeepingDbUrl, TransactionWrapper.DB.HOUSEKEEPING);
            LiquibaseUtil.releaseResources();
        }
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);

        setupScheduler(cfg);
        setupTaskReceiver(cfg, pubSubProject);
        setupFileScanResultReceiver(cfg, pubSubProject);

        pubsubConnectionManager = new PubSubConnectionManager(usePubSubEmulator);
        if (usePubSubEmulator) {
            ProjectTopicName projectTopicName = ProjectTopicName.of(pubSubProject,
                    cfg.getString(ConfigFile.PUBSUB_TASKS_RESULT_TOPIC));
            pubsubConnectionManager.createTopicIfNotExists(projectTopicName);
            ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(pubSubProject,
                    cfg.getString(ConfigFile.PUBSUB_TASKS_SUB));
            pubsubConnectionManager.createSubscriptionIfNotExists(projectSubscriptionName, projectTopicName);
        }
        pubSubTaskConnectionService = new PubSubTaskConnectionService(
                pubsubConnectionManager,
                pubSubProject,
                cfg.getString(ConfigFile.PUBSUB_TASKS_SUB),
                cfg.getString(ConfigFile.PUBSUB_TASKS_RESULT_TOPIC),
                ConfigUtil.getIntOrElse(cfg, PUBSUB_TASKS_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT,
                        DEFAULT_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT_SEC),
                new PubSubTaskProcessorFactoryImpl());
        try {
            pubSubTaskConnectionService.create();
        } catch (PubSubTaskException e) {
            log.error("Failed to init PubSubTask API", e);
        }

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            JdbiMessageDestination messageDestinationDao = handle.attach(JdbiMessageDestination.class);
            for (String topicName : messageDestinationDao.getAllTopics()) {
                log.info("Initializing subscription for topic {}", topicName);
                ProjectTopicName projectTopicName = ProjectTopicName.of(pubSubProject, topicName);
                pubsubConnectionManager.createTopicIfNotExists(projectTopicName);
                // todo arz investigate topic naming vs. subscription naming
                ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(
                        projectTopicName.getProject(), topicName);
                Subscription subscription = pubsubConnectionManager
                        .createSubscriptionIfNotExists(projectSubscriptionName, projectTopicName);
                // in the real world, listen differently
                setupMessageReceiver(pubsubConnectionManager, projectSubscriptionName, cfg, sendGridSupplier);
            }
        });

        synchronized (startupMonitor) {
            startupMonitor.notify();
        }

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();
        PubSubMessageBuilder messageBuilder = new PubSubMessageBuilder(cfg);
        StackdriverMetricsTracker heartbeatMonitor;

        heartbeatMonitor = new StackdriverMetricsTracker(StackdriverCustomMetric.HOUSEKEEPING_CYCLES,
                PointsReducerFactory.buildMaxPointReducer());


        //loop to pickup pending events on main DB API and create messages to send over to Housekeeping
        while (!stop) {
            try {
                // in one transaction, query the list of events to consider dispatching
                final List<QueuedEventDto> pendingEvents = new ArrayList<>();
                TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                    EventDao eventDao = apisHandle.attach(EventDao.class);
                    // first query the full list of pending events, shuffled to avoid event starvation
                    log.info("Querying pending events");
                    pendingEvents.addAll(eventDao.findPublishableQueuedEvents());
                    log.info("Found {} events that may be publishable", pendingEvents.size());
                    Collections.shuffle(pendingEvents);
                });

                for (QueuedEventDto pendingEvent : pendingEvents) {
                    // for each event we are considering, handle it in its own transaction
                    TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                        boolean shouldSkipEvent = false;
                        JdbiQueuedEvent queuedEventDao = apisHandle.attach(JdbiQueuedEvent.class);
                        User participant = null;
                        if (pendingEvent.getParticipantGuid() != null) {
                            participant = apisHandle.attach(UserDao.class)
                                    .findUserByGuid(pendingEvent.getParticipantGuid())
                                    .orElse(null);
                            if (participant == null) {
                                log.error("Could not find participant {} for publishing queued event {}, skipping",
                                        pendingEvent.getParticipantGuid(), pendingEvent.getQueuedEventId());
                                shouldSkipEvent = true;
                            } else if (participant.isTemporary()) {
                                log.warn("Participant {} for queued event {} is a temporary user, skipping",
                                        pendingEvent.getParticipantGuid(), pendingEvent.getQueuedEventId());
                                shouldSkipEvent = true;
                            }
                        }

                        if (!shouldSkipEvent) {
                            String pendingEventId = pendingEvent.getDdpEventId();
                            boolean shouldCancel = false;
                            if (StringUtils.isNotBlank(pendingEvent.getCancelCondition())) {
                                try {
                                    shouldCancel = pexInterpreter.eval(pendingEvent.getCancelCondition(),
                                            apisHandle,
                                            pendingEvent.getParticipantGuid(),
                                            pendingEvent.getOperatorGuid(),
                                            null);
                                } catch (PexException e) {
                                    log.warn("Failed to evaluate cancelCondition pex, defaulting to false: `{}`",
                                            pendingEvent.getCancelCondition(), e);
                                    shouldCancel = false;
                                }
                            }
                            if (shouldCancel) {
                                log.info("Deleting queued event {} because its cancel condition has been met", pendingEvent
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
                                                pendingEvent.getOperatorGuid(),
                                                null);
                                    } catch (PexException e) {
                                        log.warn("Failed to evaluate precondition pex, defaulting to false: `{}`",
                                                pendingEvent.getPrecondition(), e);
                                        hasMetPrecondition = false;
                                    }
                                }

                                if (hasMetPrecondition) {
                                    shouldSkipEvent = handleDelayedEventActions(apisHandle, participant, pendingEvent);

                                    if (!shouldSkipEvent) {
                                        TransactionWrapper.useTxn(TransactionWrapper.DB.HOUSEKEEPING, housekeepingHandle -> {
                                            JdbiEvent jdbiEvent = housekeepingHandle.attach(JdbiEvent.class);
                                            JdbiMessage jdbiMessage = housekeepingHandle.attach(JdbiMessage.class);
                                            if (jdbiEvent.shouldHandleEvent(
                                                    pendingEventId,
                                                    pendingEvent.getActionType().name(),
                                                    pendingEvent.getMaxOccurrencesPerUser())) {
                                                String ddpMessageId = Long.toString(jdbiMessage.insertMessageForEvent(
                                                        pendingEventId));
                                                log.info("Publishing queued event {}", pendingEvent.getQueuedEventId());
                                                PubsubMessage message = null;
                                                try {
                                                    message = messageBuilder.createMessage(ddpMessageId, pendingEvent,
                                                            apisHandle);
                                                } catch (NoSendableEmailAddressException e) {
                                                    boolean shouldDeleteEvent = apisHandle.attach(StudyDao.class)
                                                            .findSettings(pendingEvent.getStudyGuid())
                                                            .map(StudySettings::shouldDeleteUnsendableEmails)
                                                            .orElse(false);
                                                    if (shouldDeleteEvent) {
                                                        log.warn("Unable to create message for event with "
                                                                        + "queued_event_id={}, proceeding to delete",
                                                                pendingEvent.getQueuedEventId(), e);
                                                        queuedEventDao.deleteAllByQueuedEventId(
                                                                pendingEvent.getQueuedEventId());
                                                        return; // Exit out of transaction wrapper and move on to next
                                                        // event.
                                                    } else {
                                                        log.error("Could not create message for event with "
                                                                        + "queued_event_id={}"
                                                                        + " because there is no email address to sent to",
                                                                pendingEvent.getQueuedEventId(), e);
                                                    }
                                                } catch (MessageBuilderException e) {
                                                    log.error("Could not create message for queued event "
                                                            + pendingEvent.getQueuedEventId(), e);
                                                }

                                                if (message != null) {
                                                    // publish the message and delete it from the queue when published
                                                    Publisher publisher;
                                                    try {
                                                        publisher = pubsubConnectionManager.getOrCreatePublisher(ProjectTopicName.of(
                                                                pubSubProject, pendingEvent.getPubSubTopic()));
                                                    } catch (IOException e) {
                                                        throw new DDPException("Could not create publisher for " + pendingEvent
                                                                .getPubSubTopic(), e);
                                                    }
                                                    ApiFuture<String> publishResult = publisher.publish(message);

                                                    int numRowsUpdated = queuedEventDao.markPending(pendingEvent.getQueuedEventId());
                                                    log.info("Marked queued event {} as pending", pendingEvent.getQueuedEventId());
                                                    if (numRowsUpdated != 1) {
                                                        throw new DaoException("Marked " + numRowsUpdated + " rows as pending for "

                                                                + "queued event " + pendingEventId);
                                                    }
                                                    setPostPublishingCallbacks(publishResult, pendingEvent.getQueuedEventId());
                                                } else {
                                                    log.error("null message for " + pendingEvent.getQueuedEventId());
                                                }
                                            } else {
                                                log.info(IGNORE_EVENT_LOG_MESSAGE + "{}", pendingEvent
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
                                    }
                                } else {
                                    log.info("Skipping event {} because its precondition has not been met", pendingEvent
                                            .getQueuedEventId());
                                }
                            }
                        }
                    });
                }

            } catch (Exception e) {
                logException(e);
            }
            // send a ping to monitoring
            heartbeatMonitor.addPoint(1, Instant.now().toEpochMilli());

            try {
                Thread.sleep(SLEEP_MILLIS);
            } catch (InterruptedException e) {
                log.info("Housekeeping interrupted during sleep", e);
            }
        }
        shutdownPubSub();
        shutdownQuartz();
        log.info("Housekeeping is shutting down");
    }

    private static void shutdownQuartz() {
        if (scheduler != null) {
            JobScheduler.shutdownScheduler(scheduler, true);
        }
    }

    private static void shutdownPubSub() {
        pubsubConnectionManager.close();
        if (fileScanResultSubscriber != null) {
            fileScanResultSubscriber.stopAsync();
        }
        if (taskSubscriber != null) {
            taskSubscriber.stopAsync();
        }
        if (pubSubTaskConnectionService != null) {
            try {
                pubSubTaskConnectionService.destroy();
            } catch (PubSubTaskException e) {
                log.error("Failed to shutdown PubSubTask API", e);
            }
        }
    }

    private static void setupScheduler(Config cfg) {
        boolean runScheduler = cfg.getBoolean(ConfigFile.RUN_SCHEDULER);
        boolean enableHKeepTasks = cfg.getBoolean(ConfigFile.PUBSUB_ENABLE_HKEEP_TASKS);
        if (runScheduler || enableHKeepTasks) {
            log.info("Booting job scheduler...");
            scheduler = JobScheduler.initializeWith(cfg);
            try {
                // Setup background jobs if scheduler is enabled.
                if (runScheduler) {
                    CheckAgeUpJob.register(scheduler, cfg);
                    CheckKitsJob.register(scheduler, cfg);
                    DataSyncJob.register(scheduler, cfg);
                    DatabaseBackupJob.register(scheduler, cfg);
                    DatabaseBackupCheckJob.register(scheduler, cfg);
                    FileUploadCleanupJob.register(scheduler, cfg);
                    FileUploadNotificationJob.register(scheduler, cfg);
                    TemporaryUserCleanupJob.register(scheduler, cfg);
                    StudyDataExportJob.register(scheduler, cfg);
                    CustomExportJob.register(scheduler, cfg);
                }
                // Setup jobs needed for housekeeping-tasks if that's enabled.
                if (cfg.getBoolean(ConfigFile.PUBSUB_ENABLE_HKEEP_TASKS)) {
                    OnDemandExportJob.register(scheduler, cfg);
                    if (!scheduler.checkExists(TemporaryUserCleanupJob.getKey())) {
                        TemporaryUserCleanupJob.register(scheduler, cfg);
                    }
                }
            } catch (Exception e) {
                JobScheduler.shutdownScheduler(scheduler, false);
                throw new DDPException("Failed to setup scheduler jobs", e);
            }
        } else {
            log.info("Housekeeping job scheduler is not set to run");
        }
    }

    private static void setupTaskReceiver(Config cfg, String projectId) {
        boolean enableHKeepTasks = cfg.getBoolean(ConfigFile.PUBSUB_ENABLE_HKEEP_TASKS);
        if (enableHKeepTasks) {
            var subName = ProjectSubscriptionName.of(projectId, cfg.getString(ConfigFile.PUBSUB_HKEEP_TASKS_SUB));
            var receiver = new HousekeepingTaskReceiver(subName, scheduler);
            taskSubscriber = Subscriber.newBuilder(subName, receiver)
                    .setParallelPullCount(1)
                    .setExecutorProvider(InstantiatingExecutorProvider.newBuilder()
                            .setExecutorThreadCount(1)
                            .build())
                    .build();
            try {
                taskSubscriber.startAsync().awaitRunning(30L, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new DDPException("Could not start housekeeping tasks subscriber", e);
            }
            log.info("Started housekeeping tasks subscriber to subscription {}", subName);
        } else {
            log.warn("Housekeeping tasks is not enabled");
        }
    }

    private static void setupFileScanResultReceiver(Config cfg, String projectId) {
        boolean enabled = cfg.getBoolean(ConfigFile.FileUploads.ENABLE_SCAN_RESULT_HANDLER);
        if (!enabled) {
            log.warn("File scan result handler is not enabled");
            return;
        }

        var subName = ProjectSubscriptionName.of(projectId,
                cfg.getString(ConfigFile.FileUploads.SCAN_RESULT_SUBSCRIPTION));

        boolean ensureDefault = cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS);
        GoogleCredentials credentials = GoogleCredentialUtil.initCredentials(ensureDefault);
        if (credentials == null) {
            throw new DDPException("Could not get bucket credentials");
        }

        var storageClient = new GoogleBucketClient(projectId, credentials);
        var receiver = new FileScanResultReceiver(storageClient,
                cfg.getString(ConfigFile.FileUploads.UPLOADS_BUCKET),
                cfg.getString(ConfigFile.FileUploads.SCANNED_BUCKET),
                cfg.getString(ConfigFile.FileUploads.QUARANTINE_BUCKET));

        // This is the default thread factory, just setting a custom name here.
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("file-scan-result-subscriber-1");
            thread.setDaemon(true);
            return thread;
        };

        // For now, handle messages one at a time so we don't overwhelm resources.
        ExecutorProvider executorProvider = InstantiatingExecutorProvider.newBuilder()
                .setThreadFactory(threadFactory)
                .setExecutorThreadCount(1)
                .build();

        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        Subscriber.Builder builder = Subscriber.newBuilder(subName, receiver)
                .setSystemExecutorProvider(executorProvider)
                .setExecutorProvider(executorProvider)
                .setParallelPullCount(1);

        startNewSubscriberWithRecovery(subName, builder, executorProvider, callbackExecutor, subscriber -> {
            log.info("Started file scan result subscriber to subscription {}", subName);
            fileScanResultSubscriber = subscriber;
        });
    }

    private static void startNewSubscriberWithRecovery(ProjectSubscriptionName subscription,
                                                       Subscriber.Builder builder,
                                                       ExecutorProvider executorProvider,
                                                       ExecutorService callbackExecutor,
                                                       Consumer<Subscriber> consumer) {
        Subscriber newSubscriber = builder.build();

        // Add handler that recursively calls back in here to renew subscriber.
        newSubscriber.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                log.error("Subscriber to subscription {} encountered unrecoverable failure from {}"
                        + ", rebuilding subscriber", subscription, from, failure);
                if (!executorProvider.getExecutor().isShutdown()) {
                    startNewSubscriberWithRecovery(subscription, builder, executorProvider, callbackExecutor, consumer);
                }
            }
        }, callbackExecutor);

        try {
            newSubscriber.startAsync().awaitRunning(30L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new DDPException("Could not start subscriber to subscription " + subscription, e);
        }

        // All set, pass new subscriber downstream.
        consumer.accept(newSubscriber);
    }

    private static boolean isTimeForLogging(long lastLogTime) {
        return Instant.now().toEpochMilli() - lastLogTime > ERROR_LOG_QUIET_TIME;
    }

    private static void logException(Exception e) {
        log.error("Housekeeping error", e);
    }

    public static void shutdown() {
        // halt the event loop
        stop();

        // stop pubsub
        shutdownPubSub();

        // stop quartz
        shutdownQuartz();

        // shut down db pool
        TransactionWrapper.reset();

        // stop spark
        Spark.stop();
    }

    public static void stop() {
        stop = true;
    }

    /**
     * Sets up post-publish callbacks.  When the event is published successfully, it's deleted from the queue.  If there
     * is an error during publishing, the event is requeued.
     */
    private static void setPostPublishingCallbacks(ApiFuture future, long queuedEventId) {
        ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
            @Override
            public void onFailure(Throwable t) {
                PUBSUB_PUBLISH_CALLBACK_EXECUTOR.execute(() -> {
                    if (t instanceof ApiException) {
                        ApiException apiException = ((ApiException) t);
                        log.error("Error while publishing queued event {}.  Pubsub returned {}", queuedEventId,
                                apiException.getStatusCode(), apiException);
                    }
                    log.error("Error while publishing queued event {}.  Re-queueing.", queuedEventId, t);
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
                    log.info("Posted queued event {} with message id {}", queuedEventId, messageId);
                    TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                        log.info("Attempting to delete queued event {}", queuedEventId);
                        int numRowsDeleted = queuedEventDao.deleteByQueuedEventId(queuedEventId);
                        if (numRowsDeleted != 1) {
                            throw new DaoException("Deleted " + numRowsDeleted + " rows for queued event "
                                    + queuedEventId);
                        } else {
                            log.info("Deleted posted event {}", queuedEventId);
                        }
                    });
                });
            }
        });
    }

    private static void setupMessageReceiver(PubSubConnectionManager pubsubConnectionManager, ProjectSubscriptionName
            projectSubscriptionName, Config cfg, SendGridSupplier sendGridSupplier) {
        PdfService pdfService = new PdfService();
        PdfBucketService pdfBucketService = new PdfBucketService(cfg);
        PdfGenerationService pdfGenerationService = new PdfGenerationService();
        PdfGenerationHandler pdfGenerationHandler = new PdfGenerationHandler(pdfService, pdfBucketService, pdfGenerationService);
        Gson gson = new Gson();
        if (sendGridSupplier == null) {
            sendGridSupplier = apiKey -> new SendGridClient(apiKey, ConfigUtil.getStrIfPresent(cfg, ConfigFile.Sendgrid.PROXY));
        }
        final SendGridSupplier sendGridProvider = sendGridSupplier;
        try {
            MessageReceiver receiver =
                    (message, consumer) -> {
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
                                    log.info("Received ddp message {} of type/version {}/{} for event {} with text {}",
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

                                        var sendGridKey = notificationMessage.getApiKey();

                                        /*
                                            SendGrid keys are in the format:
                                            SG.<key-id>.<key>
                                            The <key-id> part is not considered secret, and
                                            can be used to refer to the key directly (SendGrid's
                                            APIs allow for this as well)
                                        */
                                        var keyParts = sendGridKey.split("\\.");

                                        /*
                                        Be a little careful here. If the key doesn't match
                                            the exact format we're looking for, note that there's
                                            an issue with the format, and don't log anything else.
                                        This is to avoid a situation where the format changes and
                                            we accidentally write the entire key to the log.
                                        */
                                        if (keyParts.length == 3 && keyParts[0].equals("SG")) {
                                            log.info("creating EmailNotificationHandler using SendGrid key id {}", keyParts[1]);
                                        } else {
                                            log.warn("SendGrid API key is in an unexpected format.");
                                        }

                                        new EmailNotificationHandler(sendGridProvider.get(sendGridKey),
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
                                    log.warn("Skipping duplicate message {} for event {}", ddpMessageId,
                                            ddpEventId);
                                }
                            });
                        } catch (MissingUserException e) {
                            log.error("We have a message for which we no longer have a user, "
                                    + "ack-ing and skipping it: ", e);
                            consumer.ack();
                        } catch (MessageHandlingException e) {
                            log.error("Trouble processing message", e);
                            if (e.shouldRetry()) {
                                consumer.nack();
                            } else {
                                consumer.ack();
                            }
                        } catch (Exception e) {
                            consumer.nack();
                            log.error("Could not make sense of message " + message.getMessageId(), e);
                        }
                    };

            Subscriber subscriber = pubsubConnectionManager.subscribeBuilder(projectSubscriptionName, receiver)
                    .setParallelPullCount(1)
                    .setExecutorProvider(InstantiatingExecutorProvider.newBuilder()
                            .setExecutorThreadCount(1)
                            .build())
                    .build();
            subscriber.startAsync();
        } catch (Exception e) {
            log.error("Error during message handling", e);
        }
    }

    private static boolean handleDelayedEventActions(Handle apisHandle, User participant, QueuedEventDto pendingEvent) {
        var eventDao = apisHandle.attach(EventDao.class);
        var jdbiQueuedEvent = apisHandle.attach(JdbiQueuedEvent.class);
        EventActionType actionType = pendingEvent.getActionType();
        if (actionType == EventActionType.ACTIVITY_INSTANCE_CREATION
                || actionType == EventActionType.UPDATE_USER_STATUS) {
            EventConfiguration event = eventDao
                    .getEventConfigurationDtoById(pendingEvent.getEventConfigurationId())
                    .map(EventConfiguration::new)
                    .orElse(null);
            if (event == null) {
                log.error("No event configuration found for id={}, skipping queued event {}",
                        pendingEvent.getEventConfigurationId(), pendingEvent.getQueuedEventId());
                return true;
            }

            long operatorUserId = pendingEvent.getOperatorUserId() != null
                    ? pendingEvent.getOperatorUserId() : participant.getId();
            String operatorGuid = pendingEvent.getOperatorGuid() != null
                    ? pendingEvent.getOperatorGuid() : participant.getGuid();
            StudyDto studyDto = new JdbiUmbrellaStudyCached(apisHandle)
                    .findByStudyGuid(pendingEvent.getStudyGuid());
            var signal = new EventSignal(
                    operatorUserId,
                    participant.getId(),
                    participant.getGuid(),
                    operatorGuid,
                    studyDto.getId(),
                    studyDto.getGuid(),
                    pendingEvent.getTriggerType());

            if (actionType == EventActionType.ACTIVITY_INSTANCE_CREATION) {
                // fixme: build an ActivityInstanceStatusChangeSignal instead
                var action = (ActivityInstanceCreationEventAction) event.getEventAction();
                action.doActionSynchronously(apisHandle, signal);
            } else {
                var action = (UpdateUserStatusEventAction) event.getEventAction();
                action.doActionSynchronously(apisHandle, signal);
            }

            jdbiQueuedEvent.deleteAllByQueuedEventId(pendingEvent.getQueuedEventId());
            apisHandle.attach(DataExportDao.class).queueDataSync(participant.getId(), studyDto.getId());
            return true;
        }

        if (actionType == EventActionType.CREATE_KIT) {
            EventConfiguration event = eventDao
                    .getEventConfigurationDtoById(pendingEvent.getEventConfigurationId())
                    .map(EventConfiguration::new)
                    .orElse(null);
            if (event == null) {
                log.error("No event configuration found for id={}, skipping queued event {}",
                        pendingEvent.getEventConfigurationId(), pendingEvent.getQueuedEventId());
                return true;
            }

            long operatorUserId = pendingEvent.getOperatorUserId() != null
                    ? pendingEvent.getOperatorUserId() : participant.getId();
            String operatorGuid = pendingEvent.getOperatorGuid() != null
                    ? pendingEvent.getOperatorGuid() : participant.getGuid();
            StudyDto studyDto = new JdbiUmbrellaStudyCached(apisHandle)
                    .findByStudyGuid(pendingEvent.getStudyGuid());
            EventSignal signal = new EventSignal(
                    operatorUserId,
                    participant.getId(),
                    participant.getGuid(),
                    operatorGuid,
                    studyDto.getId(),
                    studyDto.getGuid(),
                    pendingEvent.getTriggerType());

            CreateKitEventAction action = (CreateKitEventAction) event.getEventAction();
            action.doActionSynchronously(apisHandle, signal);
            jdbiQueuedEvent.delete(pendingEvent.getQueuedEventId());
            log.info("Deleted queued create kit event {}", pendingEvent.getQueuedEventId());
            return true;
        }

        return false;
    }

    private static boolean checkUserExists(String participantGuid) {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, apihandle ->
                apihandle.attach(UserDao.class).findUserByGuid(participantGuid).isPresent());
    }

    /**
     * Callback fired after a message is handled.  Primarily used for testing.
     */
    public interface AfterHandlerCallback {

        /**
         * Method runs immediately after a message is handled
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

    /**
     * Supplier of SendGrid service.
     */
    @FunctionalInterface
    interface SendGridSupplier {
        SendGridClient get(String apiKey);
    }
}
