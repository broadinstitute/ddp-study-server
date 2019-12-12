package org.broadinstitute.ddp.monitoring;

import java.util.function.BinaryOperator;

import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.Point;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not a real test, just a useful place to mess around with
 * stackdriver metrics
 */
@Ignore
public class MonitorTest {

    private static final Logger LOG = LoggerFactory.getLogger(MonitorTest.class);

    /**
     * Stackdriver doesn't seem to show the series label unless
     * you have a few different series, so here we send
     * some to SD
     */
    @Test
    @Ignore
    public void initializeMetrics() throws Exception {
        for (StackdriverCustomMetric metric : StackdriverCustomMetric.values()) {
            new StackdriverMetricsTracker(
                    metric,
                    "testing1",
                    PointsReducerFactory.buildMaxPointReducer()).addPoint(1, System.currentTimeMillis());
            new StackdriverMetricsTracker(
                    metric,
                    "testing2",
                    PointsReducerFactory.buildMaxPointReducer()).addPoint(1, System.currentTimeMillis());
        }
        Thread.sleep(62 * 1000);
    }

    /**
     * Deletes all metrics--this should basically never be run.
     */
    private void deleteAllCustomMetrics() throws Exception {
        for (StackdriverCustomMetric metric : StackdriverCustomMetric.values()) {

            try (MetricServiceClient client = MetricsServiceUtil.createClient()) {
                String metricName =
                        "projects/" + StackdriverMetricsTracker.PROJECT_NAME.getProject() + "/metricDescriptors/custom.googleapis.com/ddp/"
                        + metric.getMetricName();
                LOG.info("Deleting metric {}", metricName);
                try {
                    client.deleteMetricDescriptor(metricName);
                } catch (Exception e) {
                    LOG.error("Could not delete {}", metricName, e);
                }
            }
        }
    }

    @Test
    @Ignore
    public void sendMetricsThatViolateTimeSeparation() throws Exception {
        BinaryOperator<Point> maxPointValueReducer = PointsReducerFactory.buildMaxPointReducer();

        StackdriverMetricsTracker study1Metrics = new StackdriverMetricsTracker(
                StackdriverCustomMetric.TESTING,
                "timetest",
                maxPointValueReducer);

        long timeNearEndOfFirstTransmssion =
                System.currentTimeMillis() + (StackdriverMetricsTransmitter.STACKDRIVER_MIN_TIME_SEPARATION_SECONDS * 1000) - 10;
        long timeNearStartOfSecondTransmission =
                System.currentTimeMillis() + (StackdriverMetricsTransmitter.STACKDRIVER_MIN_TIME_SEPARATION_SECONDS * 1000) + 10;

        study1Metrics.addPoint(1, timeNearEndOfFirstTransmssion);
        Thread.sleep(StackdriverMetricsTransmitter.STACKDRIVER_THROTTLE_MILLIS);
        study1Metrics.addPoint(2, timeNearStartOfSecondTransmission);
        Thread.sleep(StackdriverMetricsTransmitter.STACKDRIVER_THROTTLE_MILLIS);
    }

    @Test
    @Ignore
    public void pureStackdriver() throws Exception {
        BinaryOperator<Point> maxPointValueReducer = PointsReducerFactory.buildMaxPointReducer();

        StackdriverMetricsTracker study1Metrics = new StackdriverMetricsTracker(
                StackdriverCustomMetric.TESTING,
                "foo",
                maxPointValueReducer);

        StackdriverMetricsTracker study2Metrics = new StackdriverMetricsTracker(
                StackdriverCustomMetric.TESTING,
                "bar",
                PointsReducerFactory.buildSumReducer());

        StackdriverMetricsTracker study3Metrics = new StackdriverMetricsTracker(
                StackdriverCustomMetric.TESTING,
                "baz",
                PointsReducerFactory.buildSumReducer());

        study3Metrics.addPoint(19, System.currentTimeMillis());

        for (int i = 0; i < 200; i++) {
            new StackdriverMetricsTracker(
                    StackdriverCustomMetric.TESTING,
                    "baz",
                    PointsReducerFactory.buildSumReducer()).addPoint(2, System.currentTimeMillis());
            new StackdriverMetricsTracker(
                    StackdriverCustomMetric.TESTING,
                    "baz",
                    PointsReducerFactory.buildSumReducer()).addPoint(5, System.currentTimeMillis());
            study1Metrics.addPoint(i * 2, System.currentTimeMillis());
            study2Metrics.addPoint(i, System.currentTimeMillis());
            study2Metrics.addPoint(i + 1, System.currentTimeMillis());
            study2Metrics.addPoint(i + 5, System.currentTimeMillis());
            Thread.sleep(30 * 1000);
        }
    }
}
