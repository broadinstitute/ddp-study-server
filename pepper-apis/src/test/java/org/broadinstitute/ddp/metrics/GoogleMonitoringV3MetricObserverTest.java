package org.broadinstitute.ddp.metrics;

import com.google.api.client.http.HttpTransport;
import com.netflix.servo.Metric;
import com.netflix.servo.tag.BasicTag;
import com.netflix.servo.tag.BasicTagList;
import com.netflix.servo.tag.Tag;
import com.netflix.servo.tag.TagList;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

//todo EOB - refactor
@Ignore
public class GoogleMonitoringV3MetricObserverTest {

    @Test
    public void testIt() throws Exception {
        throw new Exception("TESTS NOT REFACTORED!!!!!!!");
        /*
        // useful snippet for debugging google APIs
        Logger httpLogger = Logger.getLogger(HttpTransport.class.getName());
        httpLogger.setLevel(Level.ALL);

        // Create a log handler which prints all log events to the console.
        ConsoleHandler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.ALL);
        httpLogger.addHandler(logHandler);
        */

        /*GoogleMonitoringV3MetricObserver observer = new GoogleMonitoringV3MetricObserver("test","broad-ddp-core");
        List<Metric> metrics = new ArrayList<>();

        String servoMetricName = "arz-test-metric4";

        List<Tag> tagsList = new ArrayList<>();
        tagsList.add(new BasicTag("count","2"));
        tagsList.add(new BasicTag("totalTime","2000"));
        TagList tags = new BasicTagList(tagsList);

        Metric metric = new Metric(servoMetricName,tags,System.currentTimeMillis(),10);

        metrics.add(metric);
        observer.updateImpl(metrics);
        */
    }
}