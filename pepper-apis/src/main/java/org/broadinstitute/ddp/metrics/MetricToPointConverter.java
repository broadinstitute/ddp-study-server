package org.broadinstitute.ddp.metrics;

import com.google.api.client.util.DateTime;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TypedValue;
import com.netflix.servo.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a Servo basic timer metric to a stackdriver point.
 */
public class MetricToPointConverter {

    public Point convertMetricToPoint(Metric m) {
        TypedValue value = new TypedValue().setDoubleValue(m.getNumberValue().doubleValue());
        Point point = new Point().setValue(value);
        DateTime metricTime = new DateTime(m.getTimestamp());
        point.setInterval(new TimeInterval().setStartTime(metricTime.toStringRfc3339()).setEndTime(metricTime.toStringRfc3339()));
        return point;
    }
}
