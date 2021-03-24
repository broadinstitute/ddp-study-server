package org.broadinstitute.ddp.metrics;

import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.publish.MetricFilter;
import com.netflix.servo.tag.Tag;

/**
 * Filters out noise in servo metrics data so that what's sent to google
 * are the right underlying metrics for various gauge, counter, and timer
 * values.  For some metrics, only _max_ values are sent.
 */
public class GoogleMonitoringServoMetricFilter implements MetricFilter {
    
    @Override
    public boolean matches(MonitorConfig monitorConfig) {
        String statistic = monitorConfig.getTags().getValue("statistic");
        String monitorType = monitorConfig.getTags().getValue("type");
        boolean isNormalized = "NORMALIZED".equalsIgnoreCase(monitorType);
        boolean isCounter = "COUNTER".equalsIgnoreCase(monitorType);
        boolean isGauge = "GAUGE".equalsIgnoreCase(monitorType);
        boolean isMaxStat = "max".equalsIgnoreCase(statistic);
        boolean isRawValue = statistic == null;
        boolean include = false;
        if (isNormalized) {
            include = isMaxStat;
        }
        else if (isCounter || isGauge) {
            include = isRawValue || isMaxStat;
        }
        return include;
    }
};
