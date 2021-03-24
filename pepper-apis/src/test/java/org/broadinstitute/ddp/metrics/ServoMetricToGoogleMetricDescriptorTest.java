package org.broadinstitute.ddp.metrics;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.netflix.servo.Metric;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.TagList;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;

//todo EOB - refactor

@Ignore
public class ServoMetricToGoogleMetricDescriptorTest {

    ServoMetricToGoogleMetricDescriptor metricCreator = new ServoMetricToGoogleMetricDescriptor();

    static Monitoring monitoring;

    @BeforeClass
    public static void setUp() throws IOException {
        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();
        GoogleCredential credential = GoogleCredential.getApplicationDefault().createScoped(Collections.singleton("https://www.googleapis.com/auth/monitoring"));
        monitoring = new Monitoring.Builder(httpTransport, jsonFactory, credential).setApplicationName("testing").build();

    }

    @Test
    public void testCreateMetric() throws Exception {
        throw new Exception("TESTS NOT REFACTORED!!!!!!!");
        /*String servoMetricName = "arz-test-metric";
        TagList tags = new BasicTagList(new ArrayList<>());
        Metric metric = new Metric(servoMetricName,tags,System.currentTimeMillis(),10);
        MetricDescriptor metricDescriptor = metricCreator.createMetricDescriptorForServoMetric(metric, "broad-ddp-core",monitoring);
        assertTrue("Servo metric " + servoMetricName + " is not in the right part of google metric " + metricDescriptor.getName(),
                metricDescriptor.getName().endsWith(metric.getConfig().getName()));*/
    }
}
