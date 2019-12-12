package org.broadinstitute.ddp.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeSeries;
import org.apache.commons.collections4.ListUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages background sending of stackdriver metrics according to
 * stackdriver rate limits.  Whether or not to send metrics can be controlled
 * by {@link ConfigFile#SEND_METRICS}.
 */
public class StackdriverMetricsTransmitter implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(StackdriverMetricsTransmitter.class);

    /**
     * last epoch time that we posted stuff to stackdriver
     */
    private static long lastStackdriverSendAttempt = Long.MAX_VALUE;

    /**
     * milliseconds that stackdriver wants us to wait between sends
     */
    static final long STACKDRIVER_THROTTLE_MILLIS = 65 * 1000;

    /**
     * Minimum time distance between two points for the same series
     */
    static final long STACKDRIVER_MIN_TIME_SEPARATION_SECONDS = 61;

    /**
     * Maximum number of timeseries allowed in a transmission
     */
    private static final int STACKDRIVER_MAX_TIMESERIES = 200;

    private static final AtomicBoolean hasSentFirstBatch = new AtomicBoolean(false);

    private final StackdriverMetricsTracker.MetricSeriesPoints metricSeriesPoints;
    private final Set<StatusCode.Code> tolerableErrorCodes = new HashSet<>();

    public StackdriverMetricsTransmitter(StackdriverMetricsTracker.MetricSeriesPoints metricSeriesPoints) {
        this.metricSeriesPoints = metricSeriesPoints;
        this.tolerableErrorCodes.addAll(Arrays.asList(
                StatusCode.Code.INTERNAL,
                StatusCode.Code.UNAVAILABLE,
                StatusCode.Code.DEADLINE_EXCEEDED));
    }

    /**
     * Walks through the queued list of points, flattening them by given reducer
     * inside of {@link #STACKDRIVER_THROTTLE_MILLIS} lest we run afoul of byzantine
     * stackdriver rate limits.
     */
    @Override
    public void run() {
        try {
            if (hasSentFirstBatch.get()) {
                waitUntilOkayToSend();
            }
            if (metricSeriesPoints.hasDataToTransmit()) {
                postQueuedMetrics(metricSeriesPoints.getProject(), metricSeriesPoints.dequeuePointsForTransmission());
            }
        } catch (Exception e) {
            LOG.error("Trouble sending stackdriver metrics processing", e);
        }
    }

    /**
     * Keeps track of the most recent times for points in a series
     * for each transmission and drops new points if they are too close
     * in time to points that have already been sent.  Necessary to avoid
     * "One or more points were written more frequently than the maximum sampling period"
     * errors from stackdriver.
     */
    private static class TimeSeriesAgeFilter {

        private static Map<Integer, Long> youngestPointForTimeSeries = new HashMap<>();

        private static boolean isTooYoung(TimeSeries timeSeries) {
            int hashCode = computeHashCode(timeSeries);
            boolean isTooYoung = false;
            if (!youngestPointForTimeSeries.containsKey(hashCode)) {
                youngestPointForTimeSeries.put(hashCode, timeSeries.getPoints(0).getInterval().getStartTime().getSeconds());
            } else {
                long newPointAge = timeSeries.getPoints(0).getInterval().getStartTime().getSeconds();
                long pointAgeDiff = Math.abs(newPointAge - youngestPointForTimeSeries.get(hashCode));

                isTooYoung = pointAgeDiff < STACKDRIVER_MIN_TIME_SEPARATION_SECONDS;
            }
            return isTooYoung;
        }

        public static void updateTimes(TimeSeries timeSeries) {
            int hashCode = computeHashCode(timeSeries);
            long seconds = timeSeries.getPoints(0).getInterval().getStartTime().getSeconds();
            if (!youngestPointForTimeSeries.containsKey(hashCode)) {
                youngestPointForTimeSeries.put(hashCode, seconds);
            } else {
                if (youngestPointForTimeSeries.get(hashCode) < seconds) {
                    youngestPointForTimeSeries.put(hashCode, seconds);
                }
            }
        }

        /**
         * Computes a hashcode for the series by hashing the metric,
         * resource, kind, and type for the series, but without the points.
         */
        private static int computeHashCode(TimeSeries timeSeries) {
            int hashCode = 17;
            hashCode = 37 * hashCode + timeSeries.getMetric().hashCode();
            hashCode = 53 * hashCode + timeSeries.getResource().hashCode();
            hashCode = 37 * hashCode + timeSeries.getMetricKind().getNumber();
            hashCode = 53 * hashCode + timeSeries.getValueType().getNumber();
            return hashCode;
        }

        public static Collection<TimeSeries> filter(Collection<TimeSeries> timeSeriesList) {
            Collection<TimeSeries> filtered = new ArrayList<>();
            for (TimeSeries timeSeries : timeSeriesList) {
                if (!isTooYoung(timeSeries)) {
                    filtered.add(timeSeries);
                } else {
                    LOG.debug("Dropping point {} because it will violate stackdriver's rate limits", timeSeries.getPoints(0));
                }
            }
            return filtered;
        }

    }

    /**
     * Sends the accumulated metrics to stackdriver
     */
    private void postQueuedMetrics(ProjectName projectName, Collection<TimeSeries> timeSeriesList) {
        synchronized (hasSentFirstBatch) {
            LOG.info("Sending {} points to stackdriver", timeSeriesList.size());
            Collection<TimeSeries> ageFilteredTimeSeries = TimeSeriesAgeFilter.filter(timeSeriesList);

            List<List<TimeSeries>> rateLimitedChunks = ListUtils.partition(new ArrayList<>(ageFilteredTimeSeries),
                    STACKDRIVER_MAX_TIMESERIES);

            try (MetricServiceClient metricServiceClient = MetricsServiceUtil.createClient()) {
                for (List<TimeSeries> chunkedTimeSeries : rateLimitedChunks) {
                    try {
                        CreateTimeSeriesRequest timeSeriesRequest = CreateTimeSeriesRequest.newBuilder()
                                .setName(projectName.toString())
                                .addAllTimeSeries(chunkedTimeSeries)
                                .build();
                        metricServiceClient.createTimeSeries(timeSeriesRequest);
                        hasSentFirstBatch.set(true);

                        for (TimeSeries timeSeries : chunkedTimeSeries) {
                            TimeSeriesAgeFilter.updateTimes(timeSeries);
                        }
                    } catch (ApiException e) {
                        StatusCode.Code errorCode = e.getStatusCode().getCode();
                        String msg = "Could not send metrics data with {} series to project {}";
                        if (tolerableErrorCodes.contains(errorCode)) {
                            LOG.warn(msg, timeSeriesList.size(), projectName.getProject(), e);
                        } else {
                            LOG.error(msg, timeSeriesList.size(), projectName.getProject(), e);
                        }
                    } catch (Exception e) {
                        LOG.error("Failed to send metrics data with {} series to project {}",
                                timeSeriesList.size(), projectName.getProject(), e);
                    }
                }
            } finally {
                lastStackdriverSendAttempt = Instant.now().toEpochMilli();
                if (hasSentFirstBatch.get()) {
                    hasSentFirstBatch.notify();
                }
            }
        }
    }

    /**
     * Returns the amount of time we need to wait before sending stuff to stackdriver
     * to avoid rate limit rejects
     */
    private long getApproximateStackdriverWaitTime() {
        long sleepTime = 0;
        if (hasSentFirstBatch.get()) {
            sleepTime = (lastStackdriverSendAttempt + STACKDRIVER_THROTTLE_MILLIS) - Instant.now().toEpochMilli();

            if (sleepTime < 0) {
                sleepTime = 0;
            } else if (sleepTime > STACKDRIVER_THROTTLE_MILLIS) {
                sleepTime = STACKDRIVER_THROTTLE_MILLIS;
            }
        }
        return sleepTime;
    }

    /**
     * Blocks until we have put enough time between the last stackdriver
     * request and the next one
     */
    private void waitUntilOkayToSend() {
        if (!hasSentFirstBatch.get()) {
            try {
                hasSentFirstBatch.wait();
            } catch (InterruptedException e) {
                LOG.info("Stackdriver wait interrupted", e);
            }
        }
        try {
            long sleepTime = getApproximateStackdriverWaitTime();
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            LOG.error("Stackdriver transmission thread interrupted while waiting", e);
        }
    }
}
