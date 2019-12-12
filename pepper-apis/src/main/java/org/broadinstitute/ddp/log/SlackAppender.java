package org.broadinstitute.ddp.log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.model.Attachment;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;
import com.typesafe.config.Config;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.environment.HostUtil;
import org.broadinstitute.ddp.filter.MDCLogBreadCrumbFilter;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appender that sends the message and stack trace into a slack channel
 */
public class SlackAppender<E> extends AppenderBase<ILoggingEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SlackAppender.class);

    private final Slack slack;

    private final String slackHookUrl;

    private final String channel;

    private static final String TITLE = "$TITLE$";

    private static final String STACK_TRACE = "$STACK_TRACE$";

    private static final String MESSAGE = "*" + TITLE  +  "*\n ```" + STACK_TRACE + "```";

    private boolean canLog = true;

    /**
     * Used for testing.  Actual deployments use {@link #SlackAppender() the no-arg constructor}
     */
    public SlackAppender(Slack slack, String slackHookUrl, String slackChannel) {
        this.slack = slack;
        this.slackHookUrl = slackHookUrl;
        this.channel = slackChannel;
    }

    /**
     * Constructor used by deployments via logback.xml
     */
    public SlackAppender() {
        slack = Slack.getInstance();
        Config cfg = ConfigManager.getInstance().getConfig();
        String slackHook = null;
        String slackChannel = null;
        if (cfg != null) {
            if (cfg.hasPath(ConfigFile.SLACK_HOOK)) {
                slackHook = cfg.getString(ConfigFile.SLACK_HOOK);
            }
            if (cfg.hasPath(ConfigFile.SLACK_CHANNEL)) {
                slackChannel = cfg.getString(ConfigFile.SLACK_CHANNEL);
            }
        }

        if (StringUtils.isBlank(slackHook)) {
            LOG.warn("No slack hook value for " + ConfigFile.SLACK_HOOK + ".  No logs will go to slack.");
            canLog = false;
        }
        if (StringUtils.isBlank(slackChannel)) {
            LOG.warn("No slack channel value for " + ConfigFile.SLACK_CHANNEL + ".  No logs will go to slack.");
            canLog = false;
        }
        this.slackHookUrl = slackHook;
        this.channel = slackChannel;
        if (canLog) {
            LOG.info("Slack alerts will be sent to " + slackChannel);
        }
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

    @Override
    protected void append(ILoggingEvent e) {
        if (canLog) {
            String exceptionMessage = getExceptionMessage(e);
            String stackTrace = getStringifiedStackTrace(e);

            String message = MESSAGE.replace(TITLE, exceptionMessage);
            message = message.replace(STACK_TRACE, stackTrace);

            List<Attachment> attachments = new ArrayList<>();
            String hostName = HostUtil.getHostName();

            attachments.add(Attachment.builder().text("from " + hostName).build());
            String logBreadCrumb = null;
            if (e.getMDCPropertyMap().containsKey(MDCLogBreadCrumbFilter.LOG_BREADCRUMB)) {
                logBreadCrumb = e.getMDCPropertyMap().get(MDCLogBreadCrumbFilter.LOG_BREADCRUMB);
            }
            if (StringUtils.isNotBlank(logBreadCrumb)) {
                attachments.add(Attachment.builder().text("grep for `" + logBreadCrumb + "`").build());
            }

            Payload messagePayload = Payload.builder()
                    .channel(channel)
                    .text(message)
                    .iconEmoji(":nerd_face:")
                    .attachments(attachments)
                    .username("Peppa").build();
            sendMessage(messagePayload);
        }
    }

    private void sendMessage(Payload payload) {
        WebhookResponse response = null;
        try {
            response = slack.send(slackHookUrl, payload);
            if (response.getCode() != 200) {
                throw new IOException("Could not post " + payload + " to slack.  Hook returned " + response
                        .getMessage());
            }
        } catch (IOException e) {
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

    private String stringifyStackTrace(StackTraceElement[] stackTrace) {
        StringBuilder stackTraceBuilder = new StringBuilder();
        for (StackTraceElement stackTraceElt : stackTrace) {
            stackTraceBuilder.append(stackTraceElt.toString()).append("\n");
        }
        return stackTraceBuilder.toString();
    }
}

