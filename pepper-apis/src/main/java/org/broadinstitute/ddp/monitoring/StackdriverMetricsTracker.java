package org.broadinstitute.ddp.monitoring;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;

import com.google.api.Metric;
import com.google.api.MonitoredResource;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.environment.HostUtil;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accumulates stackdriver metrics {@link Point}s and sends them to sendgrid
 * asynchronously, adhering to stackdriver's rate limits.  Individual points are
 * generally not sent to due rate limits.  Instead, the max value recorded between
 * stackdriver transmission times are sent.
 */
public class StackdriverMetricsTracker {

    private static final Logger LOG = LoggerFactory.getLogger(StackdriverMetricsTracker.class);

    public static final ProjectName PROJECT_NAME = ProjectName.of(
            ConfigManager.getInstance().getConfig().getString(ConfigFile.GOOGLE_PROJECT_ID));
    public static final boolean DO_STACKDRIVER = ConfigManager.getInstance().getConfig().getBoolean(ConfigFile.SEND_METRICS);
    private static final String HTTP_METHOD_LABEL = "http_method";
    private static final String CLIENT_LABEL = "auth0_client";
    private static final String PARTICIPANT_LABEL = "participant";
    private static final PointsStartTimeComparator pointsTimeComparator = new PointsStartTimeComparator();
    /**
     * Executor that attempts to post metrics to stackdriver in a different
     * thread from the one that accumulates points
     */
    private static ScheduledExecutorService executorService;

    private final MetricsSeries metricsSeries;

    private static final String ENDPOINT_LABEL = "endpoint";

    private static final String HTTP_STATUS_LABEL = "http_status";

    private static final String STUDY_LABEL = "study";

    private static final MetricSeriesPoints metricSeriesPoints = new MetricSeriesPoints();

    public static class MetricSeriesPoints {

        /**
         * List of cached points to send to stackdriver keyed by the metrics series.
         */
        private static final Map<MetricsSeries, Collection<Point>> pointsForSeries = new ConcurrentHashMap<>();

        private boolean hasUntransmittedData = false;

        public ProjectName getProject() {
            return PROJECT_NAME;
        }

        private void queuePoint(MetricsSeries series, long value, long epochTimeMillis) {
            Timestamp time = Timestamps.fromMillis(epochTimeMillis);
            TimeInterval interval = TimeInterval.newBuilder()
                    .setStartTime(time)
                    .setEndTime(time)
                    .build();
            TypedValue typedValue = TypedValue.newBuilder()
                    .setInt64Value(value)
                    .build();

            Point point = Point.newBuilder()
                    .setInterval(interval)
                    .setValue(typedValue)
                    .build();

            pointsForSeries.get(series).add(point);
            hasUntransmittedData = true;
        }

        private Collection newOrderedTimeSeries() {
            return new TreeSet<>((TimeSeries t1, TimeSeries t2) -> {
                if (t1.getPointsList().size() > 1 || t2.getPointsList().size() > 1) {
                    throw new DDPException("TimeSeries can only have a single point");
                } else {
                    return pointsTimeComparator.compare(t1.getPoints(0), t2.getPoints(0));
                }
            });
        }

        /**
         * Builds the {@link TimeSeries} for transmission to stackdriver and
         * clears the list of points, assuming that the caller will transmit
         * the series.
         */
        public Collection<TimeSeries> dequeuePointsForTransmission() {
            Collection<TimeSeries> timeSeriesList = newOrderedTimeSeries();

            for (Map.Entry<MetricsSeries, Collection<Point>> metricsSeries : pointsForSeries.entrySet()) {
                MetricsSeries metricMetadata = metricsSeries.getKey();
                Collection<Point> points = metricsSeries.getValue();

                Metric metric = Metric.newBuilder()
                        .setType(metricMetadata.getMetricDescriptorType())
                        .putAllLabels(metricMetadata.getSeriesLabels())
                        .build();

                Map<String, String> resourceLabels = new HashMap<>();
                resourceLabels.put("project_id", metricMetadata.getProjectName().getProject());
                resourceLabels.put("node_id", HostUtil.getGAEInstanceOrHostName());
                resourceLabels.put("location", "us-central1-a");
                resourceLabels.put("namespace", "org.datadonationplatform");
                MonitoredResource resource = MonitoredResource.newBuilder()
                        .setType("generic_node")
                        .putAllLabels(resourceLabels)
                        .build();

                Point reducedPoint = points.stream().reduce(metricMetadata.getPointsReducer()).orElse(null);

                points.clear();

                if (reducedPoint != null) {
                    TimeSeries timeSeries = TimeSeries.newBuilder()
                            .setMetric(metric)
                            .setResource(resource)
                            .addPoints(reducedPoint)
                            .build();
                    timeSeriesList.add(timeSeries);
                }

            }
            hasUntransmittedData = false;
            return timeSeriesList;
        }

        public void addSeries(MetricsSeries metricsSeries) {
            synchronized (pointsForSeries) {
                if (!pointsForSeries.containsKey(metricsSeries)) {
                    // stackdriver requires points to be in order from oldest to newest, and since there are multiple
                    // threads flying, we need a concurrent version of the set
                    AbstractSet<Point> pointStartTimeOrderedSet = new ConcurrentSkipListSet<>(pointsTimeComparator);
                    pointsForSeries.put(metricsSeries, pointStartTimeOrderedSet);
                }
            }
        }

        public boolean hasDataToTransmit() {
            return hasUntransmittedData;
        }
    }

    /**
     * Creates a new one
     *
     * @param metric        the metric to use
     * @param studyName     the study, which is used as a series label
     * @param pointsReducer when there are too many points to play nice with google's rate limits,
     *                      this is used to reduce the list to a single point
     */
    public StackdriverMetricsTracker(StackdriverCustomMetric metric,
                                     String studyName,
                                     BinaryOperator<Point> pointsReducer) {
        this(STUDY_LABEL,
                studyName,
                metric.getMetricName(),
                pointsReducer);
    }

    /**
     * Creates a new one
     *
     * @param metric        the metric to use
     * @param pointsReducer when there are too many points to play nice with google's rate limits,
     *                      this is used to reduce the list to a single point
     */
    public StackdriverMetricsTracker(StackdriverCustomMetric metric,
                                     BinaryOperator<Point> pointsReducer) {
        this(Collections.emptyMap(),
                metric.getMetricName(),
                pointsReducer);
    }

    public StackdriverMetricsTracker(StackdriverCustomMetric metric,
                                     String studyGuid,
                                     String endpoint,
                                     String httpMethod,
                                     String client,
                                     String participant,
                                     int status,
                                     BinaryOperator<Point> reducer) {
        this(metric, reducer, new LinkedHashMap<String, String>() {
            {
                if (StringUtils.isBlank(endpoint)) {
                    throw new DDPException("When tracking endpoint activity, you must give an endpoint");
                }
                if (StringUtils.isNotBlank(studyGuid)) {
                    put(STUDY_LABEL, studyGuid);
                }
                if (StringUtils.isNotBlank(client)) {
                    put(CLIENT_LABEL, client);
                }
                if (StringUtils.isNotBlank(participant)) {
                    put(PARTICIPANT_LABEL, participant);
                }
                put(ENDPOINT_LABEL, endpoint);
                put(HTTP_STATUS_LABEL, Integer.toString(status));
                put(HTTP_METHOD_LABEL, httpMethod);
            }
        });
    }

    public StackdriverMetricsTracker(StackdriverCustomMetric metric, BinaryOperator<Point> reducer, Map<String, String> labels) {
        this(labels, metric.getMetricName(), reducer);
    }

    private static String toMetricDescriptorType(String simpleMetricName) {
        return "custom.googleapis.com/ddp/" + simpleMetricName;
    }

    /**
     * Creates a new tracker
     *
     * @param labels            labels for the series
     * @param metricName        the google-compliant name of the metric
     * @param pointsReducer     reducer user to get a single point value in cases where
     *                          we have accumulated too many points for google to handle
     */
    private StackdriverMetricsTracker(Map<String, String> labels,
                                      String metricName,
                                      BinaryOperator<Point> pointsReducer) {
        initializeSendingThread();
        metricsSeries = new MetricsSeries(PROJECT_NAME, toMetricDescriptorType(metricName), labels,
                pointsReducer);
        metricSeriesPoints.addSeries(metricsSeries);
    }

    /**
     * Creates a new builder
     *
     * @param seriesLabelKey    a single key for the series label
     * @param seriesLabelValue  a single value for the series label
     * @param metricName        the google-compliant name of the metric
     * @param pointsReducer     reducer user to get a single point value in cases where
     *                          we have accumulated too many points for google to handle
     */
    private StackdriverMetricsTracker(String seriesLabelKey,
                                      String seriesLabelValue,
                                      String metricName,
                                      BinaryOperator<Point> pointsReducer) {
        initializeSendingThread();

        Map<String, String> labels = Collections.emptyMap();

        if (StringUtils.isNoneBlank(seriesLabelKey, seriesLabelValue)) {
            labels = Collections.singletonMap(seriesLabelKey, seriesLabelValue);
        }

        metricsSeries = new MetricsSeries(PROJECT_NAME, toMetricDescriptorType(metricName), labels,
                pointsReducer);
        metricSeriesPoints.addSeries(metricsSeries);
    }

    /**
     * Adds a point to this series, setting stackdriver start/end time
     * to epochTimeMillis
     */
    public void addPoint(long value, long epochTimeMillis) {
        if (DO_STACKDRIVER) {
            metricSeriesPoints.queuePoint(metricsSeries, value, epochTimeMillis);
        }
    }

    /**
     * Sets up the executor schedule for attempting to send data to stackdriver
     */
    private static synchronized void initializeSendingThread() {
        if (executorService == null) {
            if (DO_STACKDRIVER) {
                executorService = Executors.newScheduledThreadPool(1);
                ScheduledFuture<?> scheduledFuture = executorService.scheduleWithFixedDelay(
                        new StackdriverMetricsTransmitter(metricSeriesPoints),
                        0,
                        StackdriverMetricsTransmitter.STACKDRIVER_THROTTLE_MILLIS,
                        TimeUnit.MILLISECONDS);
            } else {
                LOG.info("Metrics alerting is turned off, skipping scheduling of stackdriver metrics transmissions");
            }
        }
    }

    private static class PointsStartTimeComparator implements Comparator<Point> {

        @Override
        public int compare(Point p1, Point p2) {
            Timestamp p1Start = p1.getInterval().getStartTime();
            Timestamp p2Start = p2.getInterval().getStartTime();

            if (p1Start.getSeconds() < p2Start.getSeconds()) {
                return -1;
            } else if (p1Start.getSeconds() > p2Start.getSeconds()) {
                return 1;
            } else {
                // they are equal, so compare nanos
                if (p1Start.getNanos() < p2Start.getNanos()) {
                    return -1;
                } else if (p1Start.getNanos() > p2Start.getNanos()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        }
    }
}
