package org.broadinstitute.ddp.metrics;

import com.netflix.servo.monitor.MaxGauge;
import com.netflix.servo.monitor.MonitorConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class GoogleMonitoringServoMetricFilterTest {

    GoogleMonitoringServoMetricFilter filter = new GoogleMonitoringServoMetricFilter();

    @Test
    public void testRawGauge() {
        MaxGauge gauge = new MaxGauge(MonitorConfig.builder("test").build());
        assertTrue(filter.matches(gauge.getConfig()));
    }

    @Test
    public void testMaxGauge() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","max").withTag("type","GAUGE").build();
        assertTrue(filter.matches(config));
    }

    @Test
    public void testMaxCounter() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","max").withTag("type","COUNTER").build();
        assertTrue(filter.matches(config));
    }

    @Test
    public void testCountCounter() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","count").withTag("type","COUNTER").build();
        assertFalse(filter.matches(config));
    }

    @Test
    public void testMinCounter() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","min").withTag("type","COUNTER").build();
        assertFalse(filter.matches(config));
    }

    @Test
    public void testMinTimer() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","min").withTag("type","NORMALIZED").build();
        assertFalse(filter.matches(config));
    }

    @Test
    public void testCountTimer() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","count").withTag("type","NORMALIZED").build();
        assertFalse(filter.matches(config));
    }

    @Test
    public void testMaxTimer() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","max").withTag("type","NORMALIZED").build();
        assertTrue(filter.matches(config));
    }

    @Test
    public void testTotalTimeTimer() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","totalTime").withTag("type","NORMALIZED").build();
        assertFalse(filter.matches(config));
    }

    @Test
    public void testMinGauge() {
        MonitorConfig config = MonitorConfig.builder("test").withTag("statistic","min").build();
        assertFalse(filter.matches(config));
    }
}
