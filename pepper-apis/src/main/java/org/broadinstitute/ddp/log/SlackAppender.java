package org.broadinstitute.ddp.log;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.thread.ThreadFactory;
import org.broadinstitute.ddp.thread.ThreadPriorities;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lossy appender that sends the message and stack trace into a slack channel.
 * For performance reasons, at most {@link #queueSize} messages
 * are propagated every  {@link #intervalInMillis} seconds.  Additional
 * calls to {@link #append(ILoggingEvent)} are ignored.
 */
public class SlackAppender<E> extends AppenderBase<ILoggingEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SlackAppender.class);

    private URI slackHookUrl;

    private String channel;

    private static final String TITLE = "$TITLE$";

    private static final String STACK_TRACE = "$STACK_TRACE$";

    private static final String MESSAGE = "*" + TITLE  +  "*\n ```" + STACK_TRACE + "```";

    private boolean canLog = true;

    private List<SlackMessagePayload> messagesToSend = Collections.synchronizedList(new ArrayList<>());

    private int queueSize;

    private int intervalInMillis;

    private HttpClient httpClient;
    private ScheduledThreadPoolExecutor executorService;

    private void init(String slackHookUrl,
                      String slackChannel,
                      Integer queueSize,
                      Integer intervalInMillis) {
        httpClient = HttpClient.newHttpClient();
        try {
            this.slackHookUrl = new URI(slackHookUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not parse " + slackHookUrl);
        }

        this.channel = slackChannel;
        if (queueSize != null) {
            this.queueSize = queueSize;
        } else {
            this.queueSize = 10;
        }

        if (intervalInMillis != null) {
            this.intervalInMillis = intervalInMillis;
        } else {
            this.intervalInMillis = 60000;
        }

        if (StringUtils.isBlank(slackHookUrl)) {
            LOG.warn("No logs will go to slack.");
            canLog = false;
        }
        if (StringUtils.isBlank(slackChannel)) {
            LOG.warn("No logs will go to slack.");
            canLog = false;
        }
        if (canLog) {
            LOG.info("At most {} slack alerts will be sent to {} every {} ms", queueSize, slackChannel, intervalInMillis);
            executorService = new ScheduledThreadPoolExecutor(1,
                    new ThreadFactory("SlackAppender", ThreadPriorities.SLACK_PRIORITY));
            executorService.scheduleWithFixedDelay(() -> {
                sendQueuedMessages();
            }, 0, this.intervalInMillis, TimeUnit.MILLISECONDS);
        } else {
            LOG.info("No slack alerts will be sent.");
        }
    }


    /**
     * Constructor used by deployments via logback.xml
     */
    public SlackAppender() {
        Config cfg = ConfigManager.getInstance().getConfig();
        String slackHook = null;
        String slackChannel = null;
        Integer configQueueSize = null;
        Integer configIntervalInMillis = null;
        if (cfg != null) {
            if (cfg.hasPath(ConfigFile.SLACK_HOOK)) {
                slackHook = cfg.getString(ConfigFile.SLACK_HOOK);
            }
            if (cfg.hasPath(ConfigFile.SLACK_CHANNEL)) {
                slackChannel = cfg.getString(ConfigFile.SLACK_CHANNEL);
            }
            if (cfg.hasPath(ConfigFile.SLACK_QUEUE_SIZE)) {
                configQueueSize = cfg.getInt(ConfigFile.SLACK_QUEUE_SIZE);
            }
            if (cfg.hasPath(ConfigFile.SLACK_INTERVAL_IN_MILLIS)) {
                configIntervalInMillis = cfg.getInt(ConfigFile.SLACK_INTERVAL_IN_MILLIS);
            }
            init(slackHook, slackChannel, configQueueSize, configIntervalInMillis);
        }


    }

    /**
     * Used only for testing.  In the real world, call {@link #SlackAppender()}.
     */
    SlackAppender(String slackHookUrl, String slackChannel, int queueSize, int intervalInMillis) {
        init(slackHookUrl, slackChannel, queueSize, intervalInMillis);
    }

    private String getExceptionMessage(ILoggingEvent e) {
        String exceptionMessage = "";
        if (e.getFormattedMessage() != null) {
            exceptionMessage = e.getFormattedMessage();
        }
        IThrowableProxy throwableProxy = e.getThrowableProxy();
        if (throwableProxy != null) {
            String causalMessage = "";
            if (throwableProxy.getCause() != null) {
                causalMessage = throwableProxy.getCause().getMessage();
            } else {
                causalMessage = throwableProxy.getMessage();
            }
            exceptionMessage += " " + causalMessage;
        }
        return exceptionMessage;
    }

    private String getStringifiedStackTrace(ILoggingEvent e) {
        String stackTrace = "";
        IThrowableProxy throwableProxy = e.getThrowableProxy();
        if (throwableProxy != null) {
            if (throwableProxy.getCause() != null) {
                stackTrace = stringifyStackTrace(throwableProxy.getCause().getStackTraceElementProxyArray());
            } else {
                stackTrace = stringifyStackTrace(throwableProxy.getStackTraceElementProxyArray());
            }
        }
        return stackTrace;
    }

    /**
     * Should only be used during testing
     */
    public synchronized void waitForClearToQueue(long timeoutMillis) {
        if (!messagesToSend.isEmpty()) {
            try {
                wait(timeoutMillis);
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for queue to clear", e);
            }
        }
    }

    private boolean isQueueFull() {
        synchronized (messagesToSend) {
            return messagesToSend.size() >= queueSize;
        }
    }

    @Override
    protected void append(ILoggingEvent e) {
        if (canLog) {
            synchronized (messagesToSend) {
                if (!isQueueFull()) {
                    String exceptionMessage = getExceptionMessage(e);
                    String stackTrace = getStringifiedStackTrace(e);

                    String message = MESSAGE.replace(TITLE, exceptionMessage);
                    message = message.replace(STACK_TRACE, stackTrace);

                    SlackMessagePayload messagePayload = new SlackMessagePayload(message, channel, "Pepper",
                            ":nerd_face:");
                    messagesToSend.add(messagePayload);
                }
            }
        }
    }

    private void sendQueuedMessages() {
        LOG.info("Sending {} messages to slack", messagesToSend.size());
        synchronized (messagesToSend) {
            for (SlackMessagePayload payload : messagesToSend) {
                sendMessage(payload);
            }
            messagesToSend.clear();
        }
    }

    private void sendMessage(SlackMessagePayload payload) {

        HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(slackHookUrl)
                    .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(payload)))
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                    .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Could not post " + payload + " to slack.  Hook returned " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Could not post error message to slack room " + channel + " with hook " + slackHookUrl, e);
        }
    }

    private String stringifyStackTrace(StackTraceElementProxy[] stackTrace) {
        StringBuilder stackTraceBuilder = new StringBuilder();
        for (StackTraceElementProxy stackTraceElt : stackTrace) {
            stackTraceBuilder.append(stackTraceElt.toString()).append("\n");
        }
        return stackTraceBuilder.toString();
    }

    static class SlackMessagePayload {

        @SerializedName("text")
        private String text;

        @SerializedName("channel")
        private String channel;

        @SerializedName("username")
        private String username;

        @SerializedName("icon_emoji")
        private String iconEmoji;

        @SerializedName("unfurl_links")
        private boolean unfurlLinks = false;

        public SlackMessagePayload(String text, String channel, String username, String iconEmoji) {
            this.text = text;
            this.channel = channel;
            this.username = username;
            this.iconEmoji = iconEmoji;
        }
    }
}

