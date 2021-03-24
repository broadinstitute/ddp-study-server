package org.broadinstitute.ddp.metrics;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.CreateTimeSeriesRequest;
import com.google.api.services.monitoring.v3.model.Empty;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.netflix.servo.Metric;
import com.netflix.servo.publish.BaseMetricObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Netflix servo metrics observer that posts
 * metrics to a google cloud monitoring service
 */
public class GoogleMonitoringV3MetricObserver extends BaseMetricObserver {

    private static final Logger logger = LoggerFactory.getLogger(GoogleMonitoringV3MetricObserver.class);

    private final String googleProjectName;
    private final String environmentType;

    public GoogleMonitoringV3MetricObserver(String name, String googleProjectName, String environmentType)
    {
        super(name);
        if (googleProjectName == null)
        {
            throw new IllegalArgumentException("googleProjectName is required");
        }
        this.googleProjectName = googleProjectName;

        this.environmentType = environmentType;
    }

    // todo arz just grab max value until GCM supports ranges properly
    // without restricting for just min or max, you send very weird data to google!

    @Override
    public synchronized void updateImpl(List<Metric> metrics) {
        try {
            if (!metrics.isEmpty()) {
                for (Metric metric : metrics) {
                    if (metric.getNumberValue().doubleValue() >= 0) {
                        // todo arz check type of basic timer

                        // todo build up a single time series for each metric and send
                        // it once instead of sending a series with only a single point.

                        Point point = new MetricToPointConverter().convertMetricToPoint(metric);

                        String metricName = metric.getConfig().getName();

                        logger.debug("Posting " + metricName + " value " + metric.getValue() + " from " + metric.getTimestamp());
                        TimeSeries timeSeries = new TimeSeries();

                        MonitoredResource monitoredResource = new MonitoredResource();
                        Map<String, String> labels = new HashMap<>();
                        labels.put("project_id", googleProjectName);
                        monitoredResource.setType("global").setLabels(labels);

                        HttpTransport httpTransport = new NetHttpTransport();
                        JsonFactory jsonFactory = new JacksonFactory();
                        GoogleCredential credential = GoogleCredential.getApplicationDefault().createScoped(Collections.singleton("https://www.googleapis.com/auth/monitoring"));

                        Monitoring monitoring = new Monitoring.Builder(httpTransport, jsonFactory, credential).setApplicationName("testing").build();
                        MetricDescriptor metricDescriptor = new ServoMetricToGoogleMetricDescriptor().createMetricDescriptorForServoMetric(metric, googleProjectName, monitoring, environmentType);

                        //todo arz how to wire metric descriptor to metric?

                        com.google.api.services.monitoring.v3.model.Metric googleMetric = new com.google.api.services.monitoring.v3.model.Metric();
                        googleMetric.setType(metricDescriptor.getType());

                        timeSeries.setMetric(googleMetric);
                        timeSeries.setResource(monitoredResource);

                        timeSeries.setPoints(Collections.singletonList(point));
                        CreateTimeSeriesRequest createTimeSeriesRequest = new CreateTimeSeriesRequest();

                        createTimeSeriesRequest.setTimeSeries(Collections.singletonList(timeSeries));
                        // todo arz refactoring creation of project name
                        Monitoring.Projects.TimeSeries.Create createdTimeSeries = monitoring.projects().timeSeries().create("projects/" + googleProjectName, createTimeSeriesRequest);

                        Empty execute = null;

                        try {
                            execute = createdTimeSeries.execute();
                        }
                        catch (Exception e) {
                            logger.warn("Error posting " + metricName + " to google :", e);
                        }
                    }
                }

            }
        }
        catch (Exception e) {
            logger.warn("Error posting metrics update to google",e);
        }
    }

}