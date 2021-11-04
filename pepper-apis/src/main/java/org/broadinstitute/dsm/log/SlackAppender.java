package org.broadinstitute.dsm.log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackAppender extends AppenderSkeleton {



    public SlackAppender() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SlackAppender.class);
    private static HttpClient httpClient;
    private static String appEnv;
    private static String schedulerName;
    private static URI slackHookUrl;
    private static String slackChannel;
    private static boolean configured = false;
    private static AtomicLong minEpochForNextJobError = new AtomicLong(0L);
    private static AtomicLong minEpochForNextError = new AtomicLong(0L);
    private static final int JOB_DELAY = 60;
    private static final int NON_JOB_DELAY = 30;
    private static final String GCP_HOST = "console.cloud.google.com";
    private static final String SECURED_SCHEME = "https";
    private static final String GCP_ERROR_PATH = "/errors";
    private static final String GCP_LOG_PATH = "/logs/query";
    private static final String GCP_ERROR_FILTER_PARAMETER = "filter";
    private static final String GCP_SERVICE_PARAMETER = "service";
    private static final String GCP_RESOURCE_TYPE = "gae_app";
    private static final String urlEncodedSlash = "%2F";
    private static final String urlEncodedEqualSign = "%3D";
    private static final String urlEncodedNewLine = "%0A";
    private static final String urlQuerySeparator = ";";

    private static String GCP_SERVICE;
    private static String ROOT_PACKAGE;
    final String JOB_ERROR_MESSAGE = String.format("This looks like a job error. Job error reporting is " +
                                    "throttled so you will only see 1 per %s minutes.", JOB_DELAY);
    final String NON_JOB_ERROR_MESSAGE = String.format("This does NOT look like a job error. " +
                            "Non-job error reporting is throttled so you will only see 1 per %s minutes.", NON_JOB_DELAY);

    @Override
    protected void append(LoggingEvent event) {
        if (configured && event.getLevel().toInt() == Level.ERROR_INT) {
            try {
                boolean jobError = schedulerName != null && event.getThreadName().contains(schedulerName);
                long currentEpoch = Utility.getCurrentEpoch();
                String linkToGcpError = buildLinkToGcpError(event);
                String linkToGcpLog = buildLinkToGcpLog();
                String errorMessageAndLocation = getErrorMessageAndLocation(event);
                if (jobError && currentEpoch >= minEpochForNextJobError.get()) {
                    this.sendSlackNotification(
                            buildMessage(errorMessageAndLocation, linkToGcpError, JOB_ERROR_MESSAGE, linkToGcpLog)
                    );
                    minEpochForNextJobError.set(currentEpoch + JOB_DELAY * 60L);
                } else if (!jobError && currentEpoch >= minEpochForNextError.get()) {
                    this.sendSlackNotification(
                            buildMessage(errorMessageAndLocation, linkToGcpError, NON_JOB_ERROR_MESSAGE, linkToGcpLog)
                    );
                    minEpochForNextError.set(currentEpoch + NON_JOB_DELAY * 60L);
                }
            } catch (Exception e) {
                logger.warn("ErrorNotificationAppender Error: " + ExceptionUtils.getStackTrace(e));
            }
        }
    }

    String buildLinkToGcpError(LoggingEvent event) {
        String filterString = event.getThrowableInformation() == null
                ? event.getRenderedMessage()
                : event.getThrowableInformation().getThrowable().toString();
        URIBuilder gcpErrorsUri = new URIBuilder();
        gcpErrorsUri.setScheme(SECURED_SCHEME);
        gcpErrorsUri.setHost(GCP_HOST);
        gcpErrorsUri.setPath(GCP_ERROR_PATH);
        gcpErrorsUri.setParameter(GCP_SERVICE_PARAMETER, GCP_SERVICE);
        gcpErrorsUri.setParameter(GCP_ERROR_FILTER_PARAMETER, filterString);
        return gcpErrorsUri.toString();
    }

    String buildLinkToGcpLog() {
        URIBuilder gcpLogUri = new URIBuilder();
        gcpLogUri.setScheme(SECURED_SCHEME);
        gcpLogUri.setHost(GCP_HOST);
        gcpLogUri.setPath(GCP_LOG_PATH);

        StringBuilder gcpLogUriWithParameters = new StringBuilder(gcpLogUri.toString());
        LocalDateTime currentDateTime = LocalDateTime.now();
        String minuteTimeRange = currentDateTime.withNano(0).minusSeconds(30).toInstant(ZoneOffset.UTC)
                + urlEncodedSlash
                + currentDateTime.withNano(0).plusSeconds(30).toInstant(ZoneOffset.UTC);

        gcpLogUriWithParameters
                .append(urlQuerySeparator)
                .append("query=")
                .append("resource.type")
                .append(urlEncodedEqualSign)
                .append(String.format("\"%s\"", GCP_RESOURCE_TYPE))
                .append(urlEncodedNewLine)
                .append("resource.labels.module_id")
                .append(urlEncodedEqualSign)
                .append(String.format("\"%s\"", GCP_SERVICE))
                .append(urlQuerySeparator)
                .append("timeRange")
                .append("=")
                .append(minuteTimeRange);
        return gcpLogUriWithParameters.toString();
    }

    String getErrorMessageAndLocation(LoggingEvent event) {
        StringBuilder errorCauseAndPlace = new StringBuilder();
        boolean isLoggerError = event.getThrowableInformation() == null;
        if (isLoggerError) {
            errorCauseAndPlace
                .append(event.getLocationInformation().fullInfo);
        } else {
            Throwable error = event.getThrowableInformation().getThrowable();
            String developerDefinedCodeErrorLocation = Arrays.stream(error.getStackTrace())
                    .map(StackTraceElement::toString)
                    .filter(s -> s.contains(ROOT_PACKAGE))
                    .collect(Collectors.joining(System.lineSeparator()));
            errorCauseAndPlace
                .append(error.toString())
                .append(System.lineSeparator())
                .append(developerDefinedCodeErrorLocation)
                .append(System.lineSeparator());
        }
        return errorCauseAndPlace.toString();
    }

    private void sendSlackNotification(String note) {
        SlackMessagePayload payload = buildSlackMessageWithPayload(note);
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
            logger.warn("Could not post error message to slack room " + slackChannel + " with hook " + slackHookUrl
                    + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    String buildMessage(String exceptionWithLocation, String linkToGcpError, String defaultErrorMessage, String linkToGcpLog) {
        StringBuilder prettifiedErrorMessage = new StringBuilder();
        prettifiedErrorMessage
                .append(exceptionWithLocation)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(linkToGcpError)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(linkToGcpLog)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(defaultErrorMessage);
        return prettifiedErrorMessage.toString();
    }

    public SlackMessagePayload buildSlackMessageWithPayload(String note) {
        return new SlackMessagePayload(note, slackChannel, "Study-Manager", ":nerd_face:");
    }

    @Override
    public void close() {

    }

    @Override
    public boolean requiresLayout() {
        return false;
    }


    public static synchronized void configure(String scheduler, String appEnv, URI slackHookUri, String slackChannel,
                                              String gcpServiceName, String rootPackage) {
        if (!configured) {
            SlackAppender.appEnv = appEnv;
            slackHookUrl = slackHookUri;
            httpClient = HttpClient.newHttpClient();
            SlackAppender.slackChannel = slackChannel;
            schedulerName = scheduler;
            GCP_SERVICE = gcpServiceName;
            ROOT_PACKAGE = rootPackage;
            configured = true;
        } else {
            throw new RuntimeException("Configure has already been called for this appender.");
        }
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
