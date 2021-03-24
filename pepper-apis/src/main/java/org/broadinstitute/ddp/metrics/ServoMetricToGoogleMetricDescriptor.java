package org.broadinstitute.ddp.metrics;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.netflix.servo.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServoMetricToGoogleMetricDescriptor {

    private static final Logger logger = LoggerFactory.getLogger(ServoMetricToGoogleMetricDescriptor.class);

    public MetricDescriptor createMetricDescriptorForServoMetric(Metric servoMetric, String googleProjectName, Monitoring monitoring, String environmentType) throws IOException {
        String servoMetricName = environmentType + "." + servoMetric.getConfig().getName();
        Monitoring.Projects projects = monitoring.projects();
        Monitoring.Projects.MetricDescriptors metricDescriptors = projects.metricDescriptors();

        MetricDescriptor metricDescriptor = new MetricDescriptor();

        metricDescriptor.setName("projects/" + googleProjectName + "/metricDescriptors/type=" + servoMetricName);
        metricDescriptor.setType("custom.googleapis.com/org/broadinstitute/" + servoMetricName);
        metricDescriptor.setMetricKind("GAUGE");
        metricDescriptor.setValueType("DOUBLE");
        // todo arz explore how to map units
        metricDescriptor.setDisplayName(servoMetricName);
        return metricDescriptors.create("projects/" + googleProjectName, metricDescriptor).execute();
    }
}
